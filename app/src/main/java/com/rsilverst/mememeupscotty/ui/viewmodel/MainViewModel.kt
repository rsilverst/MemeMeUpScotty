package com.rsilverst.mememeupscotty.ui.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Display names live in res/values/strings.xml as model_*_name via the
// ui.ImageModelMetadata extension props; the `id` is the Replicate path.
enum class ImageModel(val id: String) {
    JUGGERNAUT("sdxl-based/juggernaut-xl-lightning"),
    STABILITY("stability-ai/sdxl"),
    REALVIS("adirik/realvisxl-v3.0-turbo"),
    FLUX_SCHNELL("black-forest-labs/flux-schnell"),
    DREAMSHAPER("lucataco/dreamshaper-xl-lightning"),
    BLUE_PENCIL("asiryan/blue-pencil-xl-v2"),
    PROTEUS("datacte/proteus-v0.5")
}

sealed class GenerationState {
    data object Idle : GenerationState()
    data object Loading : GenerationState()
    data class Success(val imageFile: File) : GenerationState()
    data class Error(val error: GenerationError) : GenerationState()
}

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val historyDir: File,
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _selectedModel = MutableStateFlow(ImageModel.JUGGERNAUT)
    val selectedModel: StateFlow<ImageModel> = _selectedModel.asStateFlow()

    // Persistent history of every image loaded onto the canvas, each carrying
    // its provenance (prompt / model / seed) and its editable captions.
    private val _generationHistory = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val generationHistory: StateFlow<List<HistoryEntry>> = _generationHistory.asStateFlow()

    // The entry currently shown on the canvas, derived from which Success file
    // is active. The UI reads its captions (live), prompt, and provenance from
    // here. Null in Idle / Loading / Error.
    val activeEntry: StateFlow<HistoryEntry?> =
        combine(_generationHistory, _generationState) { history, state ->
            val file = (state as? GenerationState.Success)?.imageFile
            history.firstOrNull { it.file == file }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Tracks the in-flight generation coroutine so it can be cancelled
    private var generationJob: Job? = null

    // Debounced writer for live caption edits to the active image.
    private var captionPersistJob: Job? = null

    private var isHistoryLoaded = false

    init {
        viewModelScope.launch(ioDispatcher) {
            var isFirstLoad = true
            dataStore.data.collect { preferences ->
                try {
                    val loaded = readHistory(preferences)
                    withContext(Dispatchers.Main) {
                        if (isFirstLoad) {
                            isFirstLoad = false
                            val currentHistory = _generationHistory.value
                            // currentHistory first so its (live, possibly newer)
                            // captions win over the persisted copy on dedupe.
                            val merged = (currentHistory + loaded)
                                .distinctBy { it.file }
                                .take(50)
                            _generationHistory.value = merged
                            isHistoryLoaded = true

                            if (currentHistory.isNotEmpty() || loaded.size > 50) {
                                viewModelScope.launch(ioDispatcher) {
                                    saveHistoryList(merged)
                                }
                            }

                            if (currentHistory.isEmpty() && _generationState.value is GenerationState.Idle) {
                                if (merged.isNotEmpty()) {
                                    _generationState.value = GenerationState.Success(merged.first().file)
                                }
                            }
                        } else {
                            // In-memory state is authoritative (all writes go
                            // through this VM), so never clobber live captions —
                            // only fold in entries that appeared on disk and
                            // aren't already tracked.
                            val current = _generationHistory.value
                            val currentFiles = current.mapTo(HashSet()) { it.file }
                            val newFromDisk = loaded.filter { it.file !in currentFiles }
                            if (newFromDisk.isNotEmpty()) {
                                _generationHistory.value = (current + newFromDisk).take(50)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logWarning(TAG, "Failed to load history list updates", e)
                }
            }
        }
    }

    fun selectModel(model: ImageModel) {
        _selectedModel.value = model
    }

    // Live-edit the captions of the currently active image. The transform is
    // applied to the freshest snapshot read from the list (not a snapshot
    // captured at composition time), so a deferred edit — e.g. the undo action
    // on a delete-caption snackbar — never clobbers concurrent edits to the
    // other caption. Updated in memory on every keystroke / drag (cheap,
    // re-emits the list); the write to DataStore is debounced so we don't hit
    // disk on every pixel but still survive a background-kill, and onCleared
    // flushes the very last edits synchronously.
    fun editActiveCaptions(transform: (CaptionSnapshot) -> CaptionSnapshot) {
        val activeFile = (_generationState.value as? GenerationState.Success)?.imageFile ?: return
        _generationHistory.value = _generationHistory.value.map {
            if (it.file == activeFile) it.copy(captions = transform(it.captions)) else it
        }
        scheduleCaptionPersist()
    }

    // Coalesce a burst of caption edits into a single write a short time after
    // the user stops editing.
    private fun scheduleCaptionPersist() {
        captionPersistJob?.cancel()
        captionPersistJob = viewModelScope.launch(ioDispatcher) {
            delay(CAPTION_PERSIST_DEBOUNCE_MS)
            saveHistoryList(_generationHistory.value)
        }
    }

    // Swap the canvas image to one the caller has already materialised on
    // disk (e.g. a copy of a gallery URI) and add it to the history strip.
    fun setLoadedImage(file: File) {
        val persistedFile = getPersistedFileTarget(file)

        // Perform physical file I/O asynchronously, then post state updates
        viewModelScope.launch(ioDispatcher) {
            persistFileOnDisk(file, persistedFile)
            withContext(Dispatchers.Main) {
                appendToHistory(HistoryEntry(persistedFile))
                _generationState.value = GenerationState.Success(persistedFile)
            }
        }
    }

    // User tapped a thumbnail in the history strip. Flips the active image and
    // restores the model that produced it; the UI restores the prompt from the
    // entry. Captions ride along with the entry, still fully editable. Persists
    // so any caption edits to the entry we're leaving are written to disk.
    fun selectFromHistory(file: File) {
        _generationState.value = GenerationState.Success(file)
        val entry = _generationHistory.value.firstOrNull { it.file == file }
        entry?.modelId?.let { id ->
            ImageModel.entries.firstOrNull { it.id == id }?.let { _selectedModel.value = it }
        }
        viewModelScope.launch(ioDispatcher) { saveHistoryList(_generationHistory.value) }
    }

    fun generateImage(prompt: String, cacheDir: File) {
        // Cancel any in-flight attempt so a rapid second tap (or a re-roll
        // mid-generation) doesn't race with the previous one.
        generationJob?.cancel()
        val modelId = _selectedModel.value.id
        generationJob = viewModelScope.launch {
            _generationState.value = GenerationState.Loading
            try {
                val outcome = imageRepository.generateImage(modelId, prompt, cacheDir)
                when (outcome) {
                    is GenerationOutcome.Success -> {
                        val persistedFile = getPersistedFileTarget(outcome.file)

                        // Perform physical file I/O asynchronously, then post state updates
                        viewModelScope.launch(ioDispatcher) {
                            persistFileOnDisk(outcome.file, persistedFile)
                            withContext(Dispatchers.Main) {
                                appendToHistory(
                                    HistoryEntry(
                                        file = persistedFile,
                                        prompt = prompt,
                                        modelId = modelId,
                                        seed = outcome.seed
                                    )
                                )
                                _generationState.value = GenerationState.Success(persistedFile)
                            }
                        }
                    }
                    is GenerationOutcome.Failure -> {
                        _generationState.value = GenerationState.Error(outcome.error)
                    }
                }
            } catch (e: CancellationException) {
                // Treat user-initiated cancellation as a return to Idle, not
                // an error. Re-throw so the coroutine machinery still sees
                // the cancellation cleanly.
                _generationState.value = GenerationState.Idle
                throw e
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
    }

    fun deleteFromHistory(file: File) {
        val newList = _generationHistory.value.filter { it.file != file }
        _generationHistory.value = newList

        viewModelScope.launch(ioDispatcher) {
            saveHistoryList(newList)
            try {
                if (file.exists() && file.parentFile == historyDir) {
                    file.delete()
                }
            } catch (e: Exception) {
                logWarning(TAG, "Failed to delete history file from disk", e)
            }
        }

        val activeState = _generationState.value
        if (activeState is GenerationState.Success && activeState.imageFile == file) {
            if (newList.isNotEmpty()) {
                _generationState.value = GenerationState.Success(newList.first().file)
            } else {
                _generationState.value = GenerationState.Idle
            }
        }
    }

    fun clearAllHistory() {
        val currentList = _generationHistory.value
        _generationHistory.value = emptyList()
        _generationState.value = GenerationState.Idle

        viewModelScope.launch(ioDispatcher) {
            saveHistoryList(emptyList())
            currentList.forEach { entry ->
                try {
                    val file = entry.file
                    if (file.exists() && file.parentFile == historyDir) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    logWarning(TAG, "Failed to clear history file from disk", e)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled by the time onCleared runs, so a
        // launch{} here would never execute. Flush the latest caption edits
        // synchronously instead — the payload is tiny, so the brief block on
        // teardown is negligible, and it guarantees the on-screen state is what
        // we reopen to.
        runBlocking { saveHistoryList(_generationHistory.value) }
    }

    private fun getPersistedFileTarget(file: File): File {
        // If the file is already in the persistent history directory, return it as-is
        if (file.parentFile == historyDir) return file
        return File(historyDir, "history_meme_${System.currentTimeMillis()}_${file.name}")
    }

    private fun isEphemeralCacheFile(file: File): Boolean {
        val path = file.absolutePath
        val name = file.name
        val isInCacheOrTemp = path.contains("cache", ignoreCase = true) ||
                path.contains("temp", ignoreCase = true) ||
                path.contains("tmp", ignoreCase = true)
        val isEphemeral = name.startsWith("generated_meme_") ||
                name.startsWith("gallery_meme_") ||
                name.startsWith("shared_meme_") ||
                name.startsWith("success_image") ||
                name.startsWith("gen-next-")
        return isInCacheOrTemp && isEphemeral
    }

    private fun persistFileOnDisk(source: File, target: File) {
        if (source == target || !source.exists()) return
        try {
            if (isEphemeralCacheFile(source)) {
                // Attempt a move/rename first, which is faster and doesn't duplicate space
                val success = source.renameTo(target)
                if (!success) {
                    // Fallback to copy and delete if rename is not supported across filesystems
                    source.copyTo(target, overwrite = true)
                    source.delete()
                }
            } else {
                source.copyTo(target, overwrite = true)
            }
        } catch (e: Exception) {
            logWarning(TAG, "Failed to persist file to history directory on disk", e)
        }
    }

    private fun appendToHistory(entry: HistoryEntry) {
        // Most-recent at index 0 so the strip's leftmost slot is always the
        // newest. If the same file is somehow re-added, drop the existing reference.
        val currentList = _generationHistory.value.filter { it.file != entry.file }
        val newList = listOf(entry) + currentList

        _generationHistory.value = newList

        if (isHistoryLoaded) {
            val toKeep = newList.take(50)
            val toDelete = newList.drop(50)
            viewModelScope.launch(ioDispatcher) {
                saveHistoryList(toKeep)
                toDelete.forEach { evicted ->
                    try {
                        val oldFile = evicted.file
                        if (oldFile.exists() && oldFile.parentFile == historyDir) {
                            oldFile.delete()
                        }
                    } catch (e: Exception) {
                        logWarning(TAG, "Failed to delete evicted history file", e)
                    }
                }
            }
            _generationHistory.value = toKeep
        }
    }

    // Reads the persisted history. Prefers the JSON entry format (provenance +
    // captions); falls back to the legacy newline-joined filename list so a
    // history written by an older build still loads (as entries with no
    // provenance and default captions). Either way, drops entries whose backing
    // file no longer exists on disk.
    private fun readHistory(preferences: Preferences): List<HistoryEntry> {
        val json = preferences[HISTORY_ENTRIES_KEY]
        if (!json.isNullOrBlank()) {
            val dtos = try {
                historyAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                logWarning(TAG, "Failed to parse history JSON; ignoring", e)
                emptyList()
            }
            return dtos.map { it.toEntry(historyDir) }.filter { it.file.exists() }
        }
        val raw = preferences[HISTORY_KEY] ?: ""
        return raw.split("\n")
            .filter { it.isNotBlank() }
            .map { HistoryEntry(File(historyDir, it)) }
            .filter { it.file.exists() }
    }

    private suspend fun saveHistoryList(list: List<HistoryEntry>) {
        try {
            val json = historyAdapter.toJson(list.map { it.toDto() })
            dataStore.edit { preferences ->
                preferences[HISTORY_ENTRIES_KEY] = json
            }
        } catch (e: Exception) {
            logWarning(TAG, "Failed to save history index to DataStore", e)
        }
    }

    private fun logWarning(tag: String, msg: String, tr: Throwable? = null) {
        try {
            Log.w(tag, msg, tr)
        } catch (_: Throwable) {
            println("[$tag] $msg ${tr?.message ?: ""}")
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        // Wait this long after the last caption edit before writing to disk.
        private const val CAPTION_PERSIST_DEBOUNCE_MS = 400L

        // Legacy key: newline-joined file names, no provenance / captions.
        private val HISTORY_KEY = stringPreferencesKey("history_paths")

        // Current key: JSON array of HistoryEntryDto.
        private val HISTORY_ENTRIES_KEY = stringPreferencesKey("history_entries")

        private val historyAdapter by lazy {
            val moshi = Moshi.Builder().build()
            val type = Types.newParameterizedType(List::class.java, HistoryEntryDto::class.java)
            moshi.adapter<List<HistoryEntryDto>>(type)
        }
    }
}
