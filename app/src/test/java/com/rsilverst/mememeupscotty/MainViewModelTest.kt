package com.rsilverst.mememeupscotty

import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MockImageRepository : ImageRepository {
    var outcomeToReturn: GenerationOutcome? = null
    var lastModelId: String? = null
    var lastPrompt: String? = null
    var lastCacheDir: File? = null

    // Gate lets the test pause the repo call mid-flight so the Loading
    // state is observable. complete() releases it.
    val gate = CompletableDeferred<Unit>()

    override suspend fun generateImage(
        modelId: String,
        prompt: String,
        cacheDir: File
    ): GenerationOutcome {
        lastModelId = modelId
        lastPrompt = prompt
        lastCacheDir = cacheDir
        gate.await()
        return outcomeToReturn
            ?: GenerationOutcome.Failure(GenerationError.Unexpected("Not configured"))
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
        val viewModel = MainViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val successFile = File("success_image.jpg")
        mockRepository.outcomeToReturn = GenerationOutcome.Success(successFile)

        assertEquals(GenerationState.Idle, viewModel.generationState.value)

        viewModel.generateImage("funny meme prompt", cacheDir)

        // Run to the first suspension point — gate.await() inside the mock —
        // which leaves the ViewModel parked on Loading.
        testDispatcher.scheduler.runCurrent()
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // Release the gate and let the coroutine finish.
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.generationState.value is GenerationState.Success)
        val successState = viewModel.generationState.value as GenerationState.Success
        assertEquals(successFile, successState.imageFile)
    }

    @Test
    fun generateImage_failure_managesStateTransitions() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val failureError = GenerationError.Unexpected("Failed to generate image")
        mockRepository.outcomeToReturn = GenerationOutcome.Failure(failureError)

        assertEquals(GenerationState.Idle, viewModel.generationState.value)

        viewModel.generateImage("funny meme prompt", cacheDir)

        testDispatcher.scheduler.runCurrent()
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.generationState.value is GenerationState.Error)
        val errorState = viewModel.generationState.value as GenerationState.Error
        assertEquals(failureError, errorState.error)
    }

    @Test
    fun generateImage_typedAuthError_propagatesAsIs() = runTest(testDispatcher) {
        // Smoke test that non-Unexpected typed variants survive the repo →
        // VM → state flow without being flattened to a string.
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository)
        mockRepository.outcomeToReturn =
            GenerationOutcome.Failure(GenerationError.AuthRejected)

        viewModel.generateImage("prompt", File("dummy_cache"))
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.generationState.value as GenerationState.Error
        assertEquals(GenerationError.AuthRejected, errorState.error)
    }

    @Test
    fun selectModel_updatesSelectedModelFlow() {
        val viewModel = MainViewModel(MockImageRepository())
        assertEquals(ImageModel.JUGGERNAUT, viewModel.selectedModel.value)

        viewModel.selectModel(ImageModel.FLUX_SCHNELL)
        assertEquals(ImageModel.FLUX_SCHNELL, viewModel.selectedModel.value)
    }

    @Test
    fun setLoadedImage_flipsStateToSuccess() {
        val viewModel = MainViewModel(MockImageRepository())
        val file = File.createTempFile("vm-loaded-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(file)

        val state = viewModel.generationState.value
        assertTrue(state is GenerationState.Success)
        assertEquals(file, (state as GenerationState.Success).imageFile)
    }

    @Test
    fun setLoadedImage_deletesPreviouslyTrackedFile() {
        val viewModel = MainViewModel(MockImageRepository())
        val first = File.createTempFile("vm-prev-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-next-", ".img").apply { deleteOnExit() }
        assertTrue(first.exists())

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)

        // First should be cleaned up; second is now the tracked image.
        assertFalse("previous file should have been deleted", first.exists())
        assertTrue("new file should still exist", second.exists())
    }

    @Test
    fun successfulGeneration_deletesPreviouslyTrackedFile() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val first = File.createTempFile("gen-prev-", ".img").apply { deleteOnExit() }

        // Seed the VM with a tracked file via setLoadedImage, then run a
        // successful generation and confirm the prior file is cleaned up.
        viewModel.setLoadedImage(first)
        assertTrue(first.exists())

        val second = File.createTempFile("gen-next-", ".img").apply { deleteOnExit() }
        mockRepository.outcomeToReturn = GenerationOutcome.Success(second)

        viewModel.generateImage("prompt", cacheDir)
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("previous tracked file should have been deleted", first.exists())
        val state = viewModel.generationState.value as GenerationState.Success
        assertEquals(second, state.imageFile)
    }
}
