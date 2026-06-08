package com.rsilverst.mememeupscotty

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.data.repository.GenerationOutcome
import com.rsilverst.mememeupscotty.data.repository.ImageRepository
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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

class FakeDataStore : DataStore<Preferences> {
    private val stateFlow = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = stateFlow

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val current = stateFlow.value
        val next = transform(current)
        stateFlow.value = next
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempHistoryDir: File

    private fun createViewModel(
        imageRepository: ImageRepository,
        historyDir: File = tempHistoryDir,
        dataStore: DataStore<Preferences> = FakeDataStore()
    ): MainViewModel {
        return MainViewModel(imageRepository, historyDir, dataStore, testDispatcher)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempHistoryDir = createTempDirectory("meme_history_test").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempHistoryDir.deleteRecursively()
    }

    @Test
    fun generateImage_success_managesStateTransitions() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = createViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val successFile = File.createTempFile("success_image", ".jpg").apply { deleteOnExit() }
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
        assertEquals(tempHistoryDir, successState.imageFile.parentFile)
        assertTrue(successState.imageFile.name.endsWith(successFile.name))
    }

    @Test
    fun generateImage_failure_managesStateTransitions() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = createViewModel(mockRepository)
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
        val viewModel = createViewModel(mockRepository)
        mockRepository.outcomeToReturn =
            GenerationOutcome.Failure(GenerationError.AuthRejected)

        viewModel.generateImage("prompt", File("dummy_cache"))
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val errorState = viewModel.generationState.value as GenerationState.Error
        assertEquals(GenerationError.AuthRejected, errorState.error)
    }

    @Test
    fun selectModel_updatesSelectedModelFlow() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        assertEquals(ImageModel.JUGGERNAUT, viewModel.selectedModel.value)

        viewModel.selectModel(ImageModel.FLUX_SCHNELL)
        assertEquals(ImageModel.FLUX_SCHNELL, viewModel.selectedModel.value)
    }

    @Test
    fun setLoadedImage_flipsStateToSuccess() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val file = File.createTempFile("vm-loaded-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(file)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.generationState.value
        assertTrue(state is GenerationState.Success)
        val successState = state as GenerationState.Success
        assertEquals(tempHistoryDir, successState.imageFile.parentFile)
        assertTrue(successState.imageFile.name.endsWith(file.name))
    }

    @Test
    fun setLoadedImage_appendsToHistoryAtHead_andDoesNotDeletePrevious() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val first = File.createTempFile("vm-prev-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-next-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)
        testDispatcher.scheduler.advanceUntilIdle()

        // Both files survive the session — the carousel is unbounded; cleanup
        // happens at cold start, not on swap.
        assertTrue("first file should NOT have been deleted", first.exists())
        assertTrue("second file should still exist", second.exists())

        // Most-recent at index 0.
        val history = viewModel.generationHistory.value
        assertEquals(2, history.size)
        assertEquals(tempHistoryDir, history[0].parentFile)
        assertEquals(tempHistoryDir, history[1].parentFile)
        assertTrue(history[0].name.endsWith(second.name))
        assertTrue(history[1].name.endsWith(first.name))
    }

    @Test
    fun selectFromHistory_flipsActiveButDoesNotMutateHistory() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val first = File.createTempFile("vm-first-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-second-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)
        testDispatcher.scheduler.advanceUntilIdle()
        val historyBefore = viewModel.generationHistory.value
        assertEquals(2, historyBefore.size)
        val firstPersisted = historyBefore[1]

        viewModel.selectFromHistory(firstPersisted)

        // Active state flipped to the older entry; history is unchanged.
        val state = viewModel.generationState.value as GenerationState.Success
        assertEquals(firstPersisted, state.imageFile)
        assertEquals(historyBefore, viewModel.generationHistory.value)
    }

    @Test
    fun cancelGeneration_duringLoading_returnsToIdle() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = createViewModel(mockRepository)
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
        val viewModel = createViewModel(mockRepository)
        val cacheDir = File("dummy_cache")
        val first = File.createTempFile("gen-prev-", ".img").apply { deleteOnExit() }

        // Seed with one history entry via setLoadedImage, then run a
        // successful generation. The new file should land at index 0 and
        // the prior entry should remain in the carousel (and on disk).
        viewModel.setLoadedImage(first)
        testDispatcher.scheduler.advanceUntilIdle()

        val second = File.createTempFile("gen-next-", ".img").apply { deleteOnExit() }
        mockRepository.outcomeToReturn = GenerationOutcome.Success(second)

        viewModel.generateImage("prompt", cacheDir)
        testDispatcher.scheduler.runCurrent() // Runs up to generating the image
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("previously loaded file should still be on disk", first.exists())
        val state = viewModel.generationState.value as GenerationState.Success
        
        val history = viewModel.generationHistory.value
        assertEquals(2, history.size)
        assertEquals(state.imageFile, history[0])
        assertEquals(tempHistoryDir, history[0].parentFile)
        assertTrue(history[0].name.endsWith(second.name))
        assertEquals(tempHistoryDir, history[1].parentFile)
        assertTrue(history[1].name.endsWith(first.name))
    }

    @Test
    fun init_capsMergedHistoryTo50Items() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val dataStore = FakeDataStore()
        
        // Seed datastore with 60 valid files that exist on disk
        val files = (1..60).map { i ->
            File(tempHistoryDir, "meme_$i.jpg").apply {
                createNewFile()
                deleteOnExit()
            }
        }
        
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("history_paths")] = files.joinToString("\n") { it.name }
        }
        
        val viewModel = createViewModel(mockRepository, dataStore = dataStore)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val history = viewModel.generationHistory.value
        assertEquals(50, history.size)
        // Verify we kept the first 50
        assertEquals(files[0].name, history[0].name)
        assertEquals(files[49].name, history[49].name)
    }

    @Test
    fun init_savesHistoryWhenCurrentHistoryIsNotEmptyDuringMerge() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val dataStore = FakeDataStore()
        
        // Seed 5 existing files on disk
        val existingFiles = (1..5).map { i ->
            File(tempHistoryDir, "existing_$i.jpg").apply {
                createNewFile()
                deleteOnExit()
            }
        }
        
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("history_paths")] = existingFiles.joinToString("\n") { it.name }
        }
        
        val viewModel = createViewModel(mockRepository, dataStore = dataStore)
        
        val loadedFile = File.createTempFile("vm-temp-", ".img").apply { deleteOnExit() }
        viewModel.setLoadedImage(loadedFile)
        
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify that the datastore has the merged history (the loadedFile + the 5 existing files)
        val raw = dataStore.data.first()[stringPreferencesKey("history_paths")] ?: ""
        val savedNames = raw.split("\n").filter { it.isNotBlank() }
        
        assertTrue(savedNames.contains(loadedFile.name))
        existingFiles.forEach { file ->
            assertTrue(savedNames.contains(file.name))
        }
    }
}
