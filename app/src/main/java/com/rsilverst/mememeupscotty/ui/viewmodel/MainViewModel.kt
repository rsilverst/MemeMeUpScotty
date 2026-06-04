package com.rsilverst.mememeupscotty.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ImageModel(val id: String, val label: String) {
    JUGGERNAUT("sdxl-based/juggernaut-xl-lightning", "Juggernaut (general purpose)"),
    STABILITY("stability-ai/sdxl", "Stability AI SDXL (best for celebrities)"),
    REALVIS("adirik/realvisxl-v3.0-turbo", "RealVisXL (best for photorealism)"),
    FLUX_SCHNELL("black-forest-labs/flux-schnell", "Flux Schnell (highest quality, no celebs)"),
    DREAMSHAPER("lucataco/dreamshaper-xl-lightning", "DreamShaper XL (alternative realism)"),
    BLUE_PENCIL("asiryan/blue-pencil-xl-v2", "Blue Pencil XL (anime / illustration)"),
    PROTEUS("datacte/proteus-v0.5", "Proteus (painterly art)")
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

    private var lastGeneratedFile: File? = null

    fun selectModel(model: ImageModel) {
        _selectedModel.value = model
    }

    // Swap the canvas image to one the caller has already materialised on
    // disk (e.g. a copy of a gallery URI). The previous tracked file is
    // deleted so cache doesn't accumulate.
    fun setLoadedImage(file: File) {
        lastGeneratedFile?.let { previous ->
            try {
                if (previous.exists()) previous.delete()
            } catch (_: Exception) {
                // Suppress deletion errors
            }
        }
        lastGeneratedFile = file
        _generationState.value = GenerationState.Success(file)
    }

    fun generateImage(prompt: String, cacheDir: File) {
        viewModelScope.launch {
            _generationState.value = GenerationState.Loading
            val outcome = imageRepository.generateImage(_selectedModel.value.id, prompt, cacheDir)
            _generationState.value = when (outcome) {
                is GenerationOutcome.Success -> {
                    lastGeneratedFile?.let { previousFile ->
                        try {
                            if (previousFile.exists()) {
                                previousFile.delete()
                            }
                        } catch (_: Exception) {
                            // Suppress deletion errors
                        }
                    }
                    lastGeneratedFile = outcome.file
                    GenerationState.Success(outcome.file)
                }
                is GenerationOutcome.Failure -> GenerationState.Error(outcome.error)
            }
        }
    }

    override fun onCleared() {
        lastGeneratedFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // Suppress deletion errors
            }
        }
    }
}
