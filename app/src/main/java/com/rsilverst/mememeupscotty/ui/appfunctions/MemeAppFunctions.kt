package com.rsilverst.mememeupscotty.ui.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.service.AppFunction
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rsilverst.mememeupscotty.historyDataStore
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.ui.viewmodel.HistoryEntryDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

// Legacy key: newline-joined file names. Current key: JSON array of
// HistoryEntryDto — kept in lock-step with MainViewModel so a headless
// generation shows up (with its prompt / model / seed) in the app's history.
private val HISTORY_KEY = stringPreferencesKey("history_paths")
private val HISTORY_ENTRIES_KEY = stringPreferencesKey("history_entries")
private val historyAdapter = run {
    val moshi = Moshi.Builder().build()
    val type = Types.newParameterizedType(List::class.java, HistoryEntryDto::class.java)
    moshi.adapter<List<HistoryEntryDto>>(type)
}

/**
 * Provides on-device background capabilities for generating meme images.
 */
class MemeAppFunctions(
    private val imageRepository: ImageRepository
) {

    /**
     * Generate a high-quality base image for custom memes using an AI generative model.
     *
     * @param appFunctionContext The execution context.
     * @param prompt The descriptive visual prompt used to generate the image (e.g., "a corgi commanding a starship").
     * @param modelId Optional. The identifier of the generative AI model to use. If omitted or null, Juggernaut XL will be used as the default.
     * @return A [MemeGenerationResult] representing the created image and metadata.
     * @throws AppFunctionAppUnknownException if the generation fails due to network, authentication, or server issues.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun generateMemeImage(
        appFunctionContext: AppFunctionContext,
        prompt: String,
        modelId: String? = null
    ): MemeGenerationResult = withContext(Dispatchers.IO) {
        val activeModelId = if (modelId.isNullOrBlank()) {
            "sdxl-based/juggernaut-xl-lightning"
        } else {
            modelId
        }

        val context = appFunctionContext.context
        val cacheDir = context.cacheDir
            ?: throw AppFunctionAppUnknownException("Cache directory is unavailable")

        val outcome = imageRepository.generateImage(activeModelId, prompt, cacheDir)

        when (outcome) {
            is GenerationOutcome.Success -> {
                val historyDir = File(context.filesDir, "history").apply { mkdirs() }
                val targetFile = File(historyDir, "history_meme_${System.currentTimeMillis()}_${outcome.file.name}")

                try {
                    outcome.file.copyTo(targetFile, overwrite = true)
                    outcome.file.delete()
                } catch (e: Exception) {
                    if (!outcome.file.renameTo(targetFile)) {
                        throw AppFunctionAppUnknownException("Failed to save image to history directory: ${e.message}")
                    }
                }

                try {
                    val prefs = context.historyDataStore.data.first()
                    // Prefer the JSON entry format; fall back to migrating the
                    // legacy filename list so pre-existing history is preserved.
                    val existing: List<HistoryEntryDto> = run {
                        val json = prefs[HISTORY_ENTRIES_KEY]
                        if (!json.isNullOrBlank()) {
                            try { historyAdapter.fromJson(json) ?: emptyList() } catch (e: Exception) { emptyList() }
                        } else {
                            val raw = prefs[HISTORY_KEY] ?: ""
                            raw.split("\n").filter { it.isNotBlank() }.map { HistoryEntryDto(file = it) }
                        }
                    }
                    val newEntry = HistoryEntryDto(
                        file = targetFile.name,
                        prompt = prompt,
                        modelId = activeModelId,
                        seed = outcome.seed
                    )
                    val merged = (listOf(newEntry) + existing.filter { it.file != newEntry.file }).take(50)
                    context.historyDataStore.edit { preferences ->
                        preferences[HISTORY_ENTRIES_KEY] = historyAdapter.toJson(merged)
                    }
                } catch (e: Exception) {
                    // Failing to write to datastore shouldn't crash the generation
                }

                MemeGenerationResult(
                    filePath = targetFile.absolutePath,
                    modelId = activeModelId,
                    prompt = prompt
                )
            }
            is GenerationOutcome.Failure -> {
                throw AppFunctionAppUnknownException("Meme generation failed: ${outcome.error}")
            }
        }
    }
}
