package com.rsilverst.mememeupscotty.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsilverst.mememeupscotty.BuildConfig
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class GenerationState {
    data object Idle : GenerationState()
    data object Loading : GenerationState()
    data class Success(val imageFile: File) : GenerationState()
    data class Error(val message: String) : GenerationState()
}

class MainViewModel(
    private val imageRepository: ImageRepository,
    private val modelId: String = BuildConfig.REPLICATE_MODEL_ID
) : ViewModel() {

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private var lastGeneratedFile: File? = null

    fun generateImage(prompt: String, cacheDir: File) {
        viewModelScope.launch {
            _generationState.value = GenerationState.Loading
            val result = imageRepository.generateImage(modelId, prompt, cacheDir)
            result.onSuccess { file ->
                lastGeneratedFile?.let { previousFile ->
                    try {
                        if (previousFile.exists()) {
                            previousFile.delete()
                        }
                    } catch (_: Exception) {
                        // Suppress deletion errors
                    }
                }
                lastGeneratedFile = file
                _generationState.value = GenerationState.Success(file)
            }.onFailure { error ->
                _generationState.value = GenerationState.Error(error.message ?: "Unknown error")
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

class MainViewModelFactory(
    private val imageRepository: ImageRepository,
    private val modelId: String = BuildConfig.REPLICATE_MODEL_ID
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(imageRepository, modelId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
