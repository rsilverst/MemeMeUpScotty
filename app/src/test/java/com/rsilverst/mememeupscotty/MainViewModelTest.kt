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
        assertEquals(tempHistoryDir, history[0].file.parentFile)
        assertEquals(tempHistoryDir, history[1].file.parentFile)
        assertTrue(history[0].file.name.endsWith(second.name))
        assertTrue(history[1].file.name.endsWith(first.name))
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
        val firstPersisted = historyBefore[1].file

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
        assertEquals(state.imageFile, history[0].file)
        assertEquals(tempHistoryDir, history[0].file.parentFile)
        assertTrue(history[0].file.name.endsWith(second.name))
        assertEquals(tempHistoryDir, history[1].file.parentFile)
        assertTrue(history[1].file.name.endsWith(first.name))
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
        assertEquals(files[0].name, history[0].file.name)
        assertEquals(files[49].name, history[49].file.name)
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

        // Verify that the datastore persisted the merged history (the loadedFile
        // + the 5 existing files) under the JSON entries key. We assert against
        // the raw JSON (which embeds each "file":"<name>") to avoid pulling a
        // Moshi adapter into the test.
        val savedJson = dataStore.data.first()[stringPreferencesKey("history_entries")] ?: ""
        assertTrue("loaded file should be persisted", savedJson.contains(loadedFile.name))
        existingFiles.forEach { file ->
            assertTrue("existing file ${file.name} should be persisted", savedJson.contains(file.name))
        }
    }

    @Test
    fun editActiveCaptions_areKeptPerEntry_andTravelOnHistorySwitch() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val first = File.createTempFile("cap-first-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("cap-second-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.editActiveCaptions { it.copy(top = it.top.copy(text = "FIRST TOP")) }

        viewModel.setLoadedImage(second)
        testDispatcher.scheduler.advanceUntilIdle()
        // A freshly loaded image starts with its own (empty) captions.
        assertEquals("", viewModel.activeEntry.value?.captions?.top?.text)
        viewModel.editActiveCaptions { it.copy(bottom = it.bottom.copy(text = "SECOND BOTTOM")) }
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch back to the first image — its caption is still there, as
        // editable text (not baked), and the second image's is untouched.
        val firstFile = viewModel.generationHistory.value.first { it.file.name.endsWith(first.name) }.file
        viewModel.selectFromHistory(firstFile)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("FIRST TOP", viewModel.activeEntry.value?.captions?.top?.text)
        assertEquals("", viewModel.activeEntry.value?.captions?.bottom?.text)

        val secondFile = viewModel.generationHistory.value.first { it.file.name.endsWith(second.name) }.file
        viewModel.selectFromHistory(secondFile)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("SECOND BOTTOM", viewModel.activeEntry.value?.captions?.bottom?.text)
        assertEquals("", viewModel.activeEntry.value?.captions?.top?.text)
    }

    @Test
    fun generation_recordsProvenance_andSelectFromHistoryRestoresModel() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = createViewModel(mockRepository)
        // Generate with a non-default model so a later restore is observable.
        viewModel.selectModel(ImageModel.FLUX_SCHNELL)
        val genFile = File.createTempFile("prov-gen-", ".img").apply { deleteOnExit() }
        mockRepository.outcomeToReturn = GenerationOutcome.Success(genFile, seed = 4242)

        viewModel.generateImage("a corgi on the bridge", File("dummy_cache"))
        testDispatcher.scheduler.runCurrent()
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val entry = viewModel.generationHistory.value.first()
        assertEquals("a corgi on the bridge", entry.prompt)
        assertEquals(ImageModel.FLUX_SCHNELL.id, entry.modelId)
        assertEquals(4242, entry.seed)

        // Load a gallery pick (no provenance) and flip the model away.
        val pick = File.createTempFile("prov-pick-", ".img").apply { deleteOnExit() }
        viewModel.setLoadedImage(pick)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectModel(ImageModel.JUGGERNAUT)

        // Tapping the generated entry restores the model that produced it.
        viewModel.selectFromHistory(entry.file)
        assertEquals(ImageModel.FLUX_SCHNELL, viewModel.selectedModel.value)
    }

    @Test
    fun captionEdits_arePersisted_andSurviveViewModelReload() = runTest(testDispatcher) {
        val dataStore = FakeDataStore()
        val file = File(tempHistoryDir, "persist-cap.img").apply { createNewFile(); deleteOnExit() }

        val vm1 = createViewModel(MockImageRepository(), dataStore = dataStore)
        vm1.setLoadedImage(file)
        testDispatcher.scheduler.advanceUntilIdle()
        vm1.editActiveCaptions { it.copy(top = it.top.copy(text = "PERSIST ME")) }
        // Let the debounced caption write run.
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate an app restart: a fresh ViewModel over the same DataStore +
        // history dir must reload the edited caption (still as editable text).
        val vm2 = createViewModel(MockImageRepository(), dataStore = dataStore)
        testDispatcher.scheduler.advanceUntilIdle()

        val reloaded = vm2.generationHistory.value.first { it.file.name.endsWith(file.name) }
        assertEquals("PERSIST ME", reloaded.captions.top.text)
    }

    @Test
    fun deleteFromHistory_removesItemAndUpdatesActiveEntry() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val first = File.createTempFile("vm-first-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-second-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.generationHistory.value.size)
        assertEquals(second.name, viewModel.activeEntry.value?.file?.name?.substringAfterLast("_"))

        val secondFileInHistory = viewModel.activeEntry.value!!.file
        viewModel.deleteFromHistory(secondFileInHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.generationHistory.value.size)
        val activeFile = viewModel.activeEntry.value?.file
        assertEquals(first.name, activeFile?.name?.substringAfterLast("_"))
        val state = viewModel.generationState.value as GenerationState.Success
        assertEquals(activeFile, state.imageFile)
    }

    @Test
    fun deleteFromHistory_removesLastItemAndSetsStateToIdle() = runTest(testDispatcher) {
        val viewModel = createViewModel(MockImageRepository())
        val first = File.createTempFile("vm-first-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.generationHistory.value.size)
        val firstFileInHistory = viewModel.activeEntry.value!!.file

        viewModel.deleteFromHistory(firstFileInHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.generationHistory.value.size)
        assertEquals(null, viewModel.activeEntry.value)
        assertEquals(GenerationState.Idle, viewModel.generationState.value)
    }

    @Test
    fun deleteFromHistory_duringInFlightGeneration_doesNotOverwriteLoadingState() = runTest(testDispatcher) {
        val mockRepository = MockImageRepository()
        val viewModel = createViewModel(mockRepository)
        val first = File.createTempFile("vm-first-", ".img").apply { deleteOnExit() }
        val second = File.createTempFile("vm-second-", ".img").apply { deleteOnExit() }

        viewModel.setLoadedImage(first)
        viewModel.setLoadedImage(second)
        testDispatcher.scheduler.advanceUntilIdle()

        val historyBefore = viewModel.generationHistory.value
        assertEquals(2, historyBefore.size)
        val secondFileInHistory = historyBefore[0].file

        // Start generation
        val cacheDir = File("dummy_cache")
        mockRepository.outcomeToReturn = GenerationOutcome.Success(File("dummy_outcome.img"))
        viewModel.generateImage("prompt", cacheDir)
        testDispatcher.scheduler.runCurrent()

        // Confirm state is Loading and active entry is null
        assertEquals(GenerationState.Loading, viewModel.generationState.value)
        assertEquals(null, viewModel.activeEntry.value)

        // Select 'second' from history while generation is Loading
        viewModel.selectFromHistory(secondFileInHistory)
        assertEquals(secondFileInHistory, viewModel.activeEntry.value?.file)
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // Delete the active entry ('second')
        viewModel.deleteFromHistory(secondFileInHistory)
        testDispatcher.scheduler.advanceUntilIdle()

        // Confirm it was removed from history, and active entry is now 'first'
        assertEquals(1, viewModel.generationHistory.value.size)
        val activeFile = viewModel.activeEntry.value?.file
        assertEquals(first.name, activeFile?.name?.substringAfterLast("_"))

        // BUT generation state must still be Loading!
        assertEquals(GenerationState.Loading, viewModel.generationState.value)

        // Now complete the generation and verify it updates to Success
        mockRepository.gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.generationState.value is GenerationState.Success)
    }
}