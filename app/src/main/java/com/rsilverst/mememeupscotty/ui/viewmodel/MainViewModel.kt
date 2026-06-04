package com.rsilverst.mememeupscotty.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val imageRepository: ImageRepository
) : ViewModel() {

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _selectedModel = MutableStateFlow(ImageModel.JUGGERNAUT)
    val selectedModel: StateFlow<ImageModel> = _selectedModel.asStateFlow()

    // Session-long carousel of every image the user has loaded onto the canvas
    // (generated or gallery-picked). Most-recent at index 0. In-memory only;
    // the on-disk files are swept on cold start by MainActivity.onCreate.
    private val _generationHistory = MutableStateFlow<List<File>>(emptyList())
    val generationHistory: StateFlow<List<File>> = _generationHistory.asStateFlow()

    // Tracks the in-flight generation coroutine so it can be cancelled —
    // either by the user via cancelGeneration(), or implicitly by a fresh
    // generateImage() call superseding the previous attempt.
    private var generationJob: Job? = null

    fun selectModel(model: ImageModel) {
        _selectedModel.value = model
    }

    // Swap the canvas image to one the caller has already materialised on
    // disk (e.g. a copy of a gallery URI) and add it to the history strip.
    fun setLoadedImage(file: File) {
        appendToHistory(file)
        _generationState.value = GenerationState.Success(file)
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
                _generationState.value = when (outcome) {
                    is GenerationOutcome.Success -> {
                        appendToHistory(outcome.file)
                        GenerationState.Success(outcome.file)
                    }
                    is GenerationOutcome.Failure -> GenerationState.Error(outcome.error)
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

    private fun appendToHistory(file: File) {
        // Most-recent at index 0 so the strip's leftmost slot is always the
        // newest. If the same file is somehow re-added (unlikely in normal
        // flow, but defensive), drop the existing reference first to avoid
        // duplicate entries.
        _generationHistory.value = listOf(file) + _generationHistory.value.filter { it != file }
    }
}
