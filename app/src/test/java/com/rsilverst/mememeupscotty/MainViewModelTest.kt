package com.rsilverst.mememeupscotty

import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MockImageRepository : ImageRepository {
    var resultToReturn: Result<File>? = null
    var lastModelId: String? = null
    var lastPrompt: String? = null
    var lastCacheDir: File? = null

    override suspend fun generateImage(
        modelId: String,
        prompt: String,
        cacheDir: File
    ): Result<File> {
        lastModelId = modelId
        lastPrompt = prompt
        lastCacheDir = cacheDir
        return resultToReturn ?: Result.failure(Exception("Not configured"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun generateImage_success_managesStateTransitions() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository, "test-model-id")
        val cacheDir = File("dummy_cache")
        val successFile = File("success_image.jpg")
        mockRepository.resultToReturn = Result.success(successFile)

        // Initially Idle
        assertEquals(GenerationState.Idle, viewModel.generationState.value)

        // Call generateImage
        viewModel.generateImage("funny meme prompt", cacheDir)

        // Coroutine is scheduled but has not executed beyond initial suspension
        testDispatcher.scheduler.runCurrent()
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // Execute remainder of coroutine
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify successful state transition and stored file
        assertTrue(viewModel.generationState.value is GenerationState.Success)
        val successState = viewModel.generationState.value as GenerationState.Success
        assertEquals(successFile, successState.imageFile)
    }

    @Test
    fun generateImage_failure_managesStateTransitions() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository, "test-model-id")
        val cacheDir = File("dummy_cache")
        val errorMessage = "Failed to generate image"
        mockRepository.resultToReturn = Result.failure(Exception(errorMessage))

        // Initially Idle
        assertEquals(GenerationState.Idle, viewModel.generationState.value)

        // Call generateImage
        viewModel.generateImage("funny meme prompt", cacheDir)

        // Coroutine is scheduled but has not executed beyond initial suspension
        testDispatcher.scheduler.runCurrent()
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // Execute remainder of coroutine
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify failure state transition and message
        assertTrue(viewModel.generationState.value is GenerationState.Error)
        val errorState = viewModel.generationState.value as GenerationState.Error
        assertEquals(errorMessage, errorState.message)
    }
}
