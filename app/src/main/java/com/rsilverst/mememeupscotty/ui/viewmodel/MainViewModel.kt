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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    // Persistent history of every image loaded onto the canvas
    private val _generationHistory = MutableStateFlow<List<File>>(emptyList())
    val generationHistory: StateFlow<List<File>> = _generationHistory.asStateFlow()

    // Tracks the in-flight generation coroutine so it can be cancelled
    private var generationJob: Job? = null

    init {
        viewModelScope.launch(ioDispatcher) {
            try {
                val preferences = dataStore.data.first()
                val raw = preferences[HISTORY_KEY] ?: ""
                val loaded = raw.split("\n")
                    .filter { it.isNotBlank() }
                    .map { File(historyDir, it) }
                    .filter { it.exists() }
                _generationHistory.value = loaded
                // Restore last active meme on canvas if history is not empty and state is Idle
                if (loaded.isNotEmpty() && _generationState.value is GenerationState.Idle) {
                    _generationState.value = GenerationState.Success(loaded.first())
                }
            } catch (e: Exception) {
                logWarning(TAG, "Failed to load history list on startup", e)
            }
        }
    }

    fun selectModel(model: ImageModel) {
        _selectedModel.value = model
    }

    // Swap the canvas image to one the caller has already materialised on
    // disk (e.g. a copy of a gallery URI) and add it to the history strip.
    fun setLoadedImage(file: File) {
        val persistedFile = getPersistedFileTarget(file)

        // Perform physical file I/O asynchronously, then post state updates
        viewModelScope.launch(ioDispatcher) {
            persistFileOnDisk(file, persistedFile)
            withContext(Dispatchers.Main) {
                appendToHistory(persistedFile)
                _generationState.value = GenerationState.Success(persistedFile)
            }
        }
    }

    // User tapped a thumbnail in the history strip. Just flips active —
    // doesn't re-add to history (the file is already there).
    fun selectFromHistory(file: File) {
        _generationState.value = GenerationState.Success(file)
    }

    fun generateImage(prompt: String, cacheDir: File) {
        // Cancel any in-flight attempt so a rapid second tap (or a re-roll
        // mid-generation) doesn't race with the previous one.
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _generationState.value = GenerationState.Loading
            try {
                val outcome = imageRepository.generateImage(_selectedModel.value.id, prompt, cacheDir)
                when (outcome) {
                    is GenerationOutcome.Success -> {
                        val persistedFile = getPersistedFileTarget(outcome.file)

                        // Perform physical file I/O asynchronously, then post state updates
                        viewModelScope.launch(ioDispatcher) {
                            persistFileOnDisk(outcome.file, persistedFile)
                            withContext(Dispatchers.Main) {
                                appendToHistory(persistedFile)
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
        val newList = _generationHistory.value.filter { it != file }
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
                _generationState.value = GenerationState.Success(newList.first())
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
            currentList.forEach { file ->
                try {
                    if (file.exists() && file.parentFile == historyDir) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    logWarning(TAG, "Failed to clear history file from disk", e)
                }
            }
        }
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

    private fun appendToHistory(file: File) {
        // Most-recent at index 0 so the strip's leftmost slot is always the
        // newest. If the same file is somehow re-added, drop the existing reference.
        val currentList = _generationHistory.value.filter { it != file }
        val newList = listOf(file) + currentList

        if (newList.size > 50) {
            val toKeep = newList.take(50)
            val toDelete = newList.drop(50)
            viewModelScope.launch(ioDispatcher) {
                saveHistoryList(toKeep)
                toDelete.forEach { oldFile ->
                    try {
                        if (oldFile.exists() && oldFile.parentFile == historyDir) {
                            oldFile.delete()
                        }
                    } catch (e: Exception) {
                        logWarning(TAG, "Failed to delete evicted history file", e)
                    }
                }
            }
            _generationHistory.value = toKeep
        } else {
            viewModelScope.launch(ioDispatcher) {
                saveHistoryList(newList)
            }
            _generationHistory.value = newList
        }
    }

    private suspend fun saveHistoryList(list: List<File>) {
        try {
            dataStore.edit { preferences ->
                preferences[HISTORY_KEY] = list.joinToString("\n") { it.name }
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
        private val HISTORY_KEY = stringPreferencesKey("history_paths")
    }
}
