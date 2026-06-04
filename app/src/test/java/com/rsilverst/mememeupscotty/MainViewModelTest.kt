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
    fun setLoadedImage_appendsToHistoryAtHead_andDoesNotDeletePrevious() {
        val viewModel = MainViewModel(MockImageRepository())
        val first = File.createTempFile("vm-prev-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-next-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)

        // Both files survive the session — the carousel is unbounded; cleanup
        // happens at cold start, not on swap.
        assertTrue("first file should NOT have been deleted", first.exists())
        assertTrue("second file should still exist", second.exists())

        // Most-recent at index 0.
        val history = viewModel.generationHistory.value
        assertEquals(listOf(second, first), history)
    }

    @Test
    fun selectFromHistory_flipsActiveButDoesNotMutateHistory() {
        val viewModel = MainViewModel(MockImageRepository())
        val first = File.createTempFile("vm-first-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-second-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)
        val historyBefore = viewModel.generationHistory.value

        viewModel.selectFromHistory(first)

        // Active state flipped to the older entry; history is unchanged.
        val state = viewModel.generationState.value as GenerationState.Success
        assertEquals(first, state.imageFile)
        assertEquals(historyBefore, viewModel.generationHistory.value)
    }

    @Test
    fun cancelGeneration_duringLoading_returnsToIdle() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository)
        mockRepository.outcomeToReturn =
            GenerationOutcome.Success(File("never_returned.img"))

        viewModel.generateImage("prompt", File("dummy_cache"))
        testDispatcher.scheduler.runCurrent()
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // User taps Cancel mid-flight. The repo gate is still closed, so the
        // mock will never complete naturally; the cancel must be what drives
        // the transition.
        viewModel.cancelGeneration()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(GenerationState.Idle, viewModel.generationState.value)
    }

    @Test
    fun successfulGeneration_prependsToHistory_andKeepsPriorEntries() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = MainViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val first = File.createTempFile("gen-prev-", ".img").apply { deleteOnExit() }

        // Seed with one history entry via setLoadedImage, then run a
        // successful generation. The new file should land at index 0 and
        // the prior entry should remain in the carousel (and on disk).
        viewModel.setLoadedImage(first)

        val second = File.createTempFile("gen-next-", ".img").apply { deleteOnExit() }
        mockRepository.outcomeToReturn = GenerationOutcome.Success(second)

        viewModel.generateImage("prompt", cacheDir)
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("previously loaded file should still be on disk", first.exists())
        val state = viewModel.generationState.value as GenerationState.Success
        assertEquals(second, state.imageFile)
        assertEquals(listOf(second, first), viewModel.generationHistory.value)
    }
}
