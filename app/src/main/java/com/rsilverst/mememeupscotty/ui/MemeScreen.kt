package com.rsilverst.mememeupscotty.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space600
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionSnapshot
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.HistoryEntry
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

// Single-screen entry point. Owns top-level state (selected model, picker
// open, snackbar host) and dispatches to the compact or expanded layout
// based on screen width. The bulk of the UI lives in sibling files in
// this package — see MemeCanvas.kt, MemeControls.kt, ModelPicker.kt,
// MemeLayouts.kt, MemeTextOverlay.kt.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeScreen(
    viewModel: MainViewModel
) {
    val generationState by viewModel.generationState.collectAsState()
    val activeEntry by viewModel.activeEntry.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val generationHistory by viewModel.generationHistory.collectAsState()
    var hasGeneratedImage by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentError = (generationState as? GenerationState.Error)?.error
    val errorTitle = currentError?.let { error ->
        when (error) {
            GenerationError.AuthRejected -> stringResource(R.string.error_title_auth)
            GenerationError.OutOfCredit -> stringResource(R.string.error_title_quota)
            GenerationError.ModelUnavailable -> stringResource(R.string.error_title_not_found)
            is GenerationError.RateLimited -> stringResource(R.string.error_title_rate_limit)
            is GenerationError.Server -> stringResource(R.string.error_title_server)
            GenerationError.Timeout -> stringResource(R.string.error_title_timeout)
            is GenerationError.Unexpected -> stringResource(R.string.error_title_generic)
        }
    }
    val errorDetail = currentError?.let { error ->
        when (error) {
            GenerationError.AuthRejected -> stringResource(R.string.error_detail_auth)
            GenerationError.OutOfCredit -> stringResource(R.string.error_detail_quota)
            GenerationError.ModelUnavailable -> stringResource(R.string.error_detail_not_found)
            is GenerationError.RateLimited -> error.retryAfterSec?.let {
                stringResource(R.string.error_detail_rate_limit_retry, it)
            } ?: stringResource(R.string.error_detail_rate_limit_no_retry)
            is GenerationError.Server -> stringResource(R.string.error_detail_server, error.httpCode)
            GenerationError.Timeout -> stringResource(R.string.error_detail_timeout)
            is GenerationError.Unexpected -> error.detail
        }
    }

    LaunchedEffect(generationState) {
        if (generationState is GenerationState.Success) {
            hasGeneratedImage = true
        } else if (generationState is GenerationState.Error && activeEntry != null) {
            if (errorTitle != null && errorDetail != null) {
                snackbarHostState.showSnackbar("$errorTitle $errorDetail")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deepSpaceBrush())
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { StardateSnackbarHost(snackbarHostState) },
            topBar = { BridgeTopBar() }
        ) { paddingValues ->
            MemeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                viewModel = viewModel,
                activeEntry = activeEntry,
                generationState = generationState,
                selectedModel = selectedModel,
                generationHistory = generationHistory,
                hasGeneratedImage = hasGeneratedImage,
                snackbarHostState = snackbarHostState,
                onOpenModelPicker = { modelPickerOpen = true }
            )
        }

        if (modelPickerOpen) {
            ModelPickerSheet(
                selected = selectedModel,
                onSelect = {
                    viewModel.selectModel(it)
                    modelPickerOpen = false
                },
                onDismiss = { modelPickerOpen = false }
            )
        }
    }
}

private fun deepSpaceBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            Space900,
            Color(0xFF151233), // Deep cosmic indigo
            Color(0xFF2E1135), // Warm nebula purple/magenta
            Color(0xFF0C2436), // Cold space teal
            Space900
        )
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BridgeTopBar() {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextHigh
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = TextHigh
        )
    )
}

@Composable
private fun StardateSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Space600,
            contentColor = TextHigh,
            actionColor = Plasma500,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .padding(16.dp)
                .border(1.dp, Space500, RoundedCornerShape(14.dp))
        )
    }
}

// ============================================================================
// State hoisting + adaptive dispatch
// Owns prompt + caption text state, save/share actions, permission flow,
// and the graphics-layer used for bitmap capture. Layouts are pure
// presentation given this state + these callbacks.
// ============================================================================

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun MemeContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    activeEntry: HistoryEntry?,
    generationState: GenerationState,
    selectedModel: ImageModel,
    generationHistory: List<HistoryEntry>,
    hasGeneratedImage: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenModelPicker: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    // Captions live with the active history entry in the ViewModel, so they
    // travel with their image and survive a reload — still as editable text,
    // never baked in until Save / Share. The canvas reads this snapshot and
    // edits it through editActiveCaptions.
    val activeEntryFromState = activeEntry
    val captions = activeEntryFromState?.captions ?: CaptionSnapshot()

    // When the active image changes (cold start, history tap, new generation),
    // restore the prompt that produced it. Keyed on the file so typing into the
    // prompt for the current image is never clobbered. Gallery picks carry no
    // prompt, so the field is left as-is for those.
    val activeFilePath = activeEntryFromState?.file?.absolutePath
    LaunchedEffect(activeFilePath) {
        activeEntryFromState?.prompt?.let { prompt = it }
    }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var capturing by remember { mutableStateOf(false) }

    // 840dp is the Material adaptive "expanded" lower bound.
    val isExpanded = LocalConfiguration.current.screenWidthDp >= 840

    val savedToGallery = stringResource(R.string.saved_to_gallery)
    val failedToSave = stringResource(R.string.failed_to_save)
    val failedToShare = stringResource(R.string.failed_to_share)
    val storagePermissionRequired = stringResource(R.string.storage_permission_required)
    val nothingToSave = stringResource(R.string.nothing_to_save)
    val nothingToShare = stringResource(R.string.nothing_to_share)
    val loadImageFailed = stringResource(R.string.load_image_failed)

    // The chrome (resize handle + delete chip) exits via a 160ms fadeOut.
    // Waiting two frames (~32ms) was racy — the saved bitmap occasionally
    // caught a half-faded handle. 200ms covers the tween plus a small
    // buffer so the recorded layer is clean by the time we snapshot.
    suspend fun captureCleanBitmap(): android.graphics.Bitmap {
        capturing = true
        delay(CAPTURE_CHROME_FADE_BUFFER_MS.milliseconds)
        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
        capturing = false
        return bitmap
    }

    val captionRemoved = stringResource(R.string.caption_removed)
    val undoLabel = stringResource(R.string.undo)
    val onCaptionDeleted: (onUndo: () -> Unit) -> Unit = { onUndo ->
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = captionRemoved,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    val performSave = {
        coroutineScope.launch {
            val bitmap = captureCleanBitmap()
            saveBitmapToGallery(context, bitmap)
                .onSuccess { snackbarHostState.showSnackbar(savedToGallery) }
                .onFailure { exception ->
                    val errMsg = exception.localizedMessage ?: exception.message ?: ""
                    snackbarHostState.showSnackbar("$failedToSave: $errMsg")
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performSave()
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar(storagePermissionRequired) }
        }
    }

    val onSaveAction: () -> Unit = {
        focusManager.clearFocus()
        if (generationState !is GenerationState.Success) {
            coroutineScope.launch { snackbarHostState.showSnackbar(nothingToSave) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            performSave()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                performSave()
            } else {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val onShareAction: () -> Unit = {
        focusManager.clearFocus()
        if (generationState !is GenerationState.Success) {
            coroutineScope.launch { snackbarHostState.showSnackbar(nothingToShare) }
        } else {
            coroutineScope.launch {
                val bitmap = captureCleanBitmap()
                shareBitmap(context, bitmap)
                    .onFailure { exception ->
                        val errMsg = exception.localizedMessage ?: exception.message ?: ""
                        snackbarHostState.showSnackbar("$failedToShare: $errMsg")
                    }
            }
        }
    }

    val onEnergize: () -> Unit = {
        if (prompt.isNotBlank()) {
            focusManager.clearFocus()
            viewModel.generateImage(prompt, context.cacheDir)
        }
    }

    val onCancel: () -> Unit = {
        viewModel.cancelGeneration()
    }

    val onSelectFromHistory: (java.io.File) -> Unit = { file ->
        viewModel.selectFromHistory(file)
    }

    val onPromptChipClick: (String) -> Unit = { suggestion ->
        prompt = suggestion
    }

    // System Photo Picker — no runtime permission needed; returns null on
    // cancel. On success we stream the URI to a cache file off the main
    // thread, then hand the File to the VM as the new canvas image.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                copyUriToCache(context, uri, context.cacheDir).fold(
                    onSuccess = { file -> viewModel.setLoadedImage(file) },
                    onFailure = { snackbarHostState.showSnackbar(loadImageFailed) }
                )
            }
        }
    }

    val onPickImage: () -> Unit = {
        focusManager.clearFocus()
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val onDeleteFromHistory: (java.io.File) -> Unit = { file ->
        viewModel.deleteFromHistory(file)
    }

    val onClearAllHistory: () -> Unit = {
        viewModel.clearAllHistory()
    }

    if (isExpanded) {
        ExpandedLayout(
            modifier = modifier,
            prompt = prompt,
            onPromptChange = { prompt = it },
            captions = captions,
            onCaptionsChange = viewModel::editActiveCaptions,
            generationState = generationState,
            selectedModel = selectedModel,
            hasGeneratedImage = hasGeneratedImage,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onOpenModelPicker = onOpenModelPicker,
            onEnergize = onEnergize,
            onCancel = onCancel,
            onSave = onSaveAction,
            onShare = onShareAction,
            onPromptChip = onPromptChipClick,
            onCaptionDeleted = onCaptionDeleted,
            onPickImage = onPickImage,
            generationHistory = generationHistory,
            activeEntry = activeEntry,
            onSelectFromHistory = onSelectFromHistory,
            onDeleteFromHistory = onDeleteFromHistory,
            onClearAllHistory = onClearAllHistory
        )
    } else {
        CompactLayout(
            modifier = modifier,
            prompt = prompt,
            onPromptChange = { prompt = it },
            captions = captions,
            onCaptionsChange = viewModel::editActiveCaptions,
            generationState = generationState,
            selectedModel = selectedModel,
            hasGeneratedImage = hasGeneratedImage,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onOpenModelPicker = onOpenModelPicker,
            onEnergize = onEnergize,
            onCancel = onCancel,
            onSave = onSaveAction,
            onShare = onShareAction,
            onPromptChip = onPromptChipClick,
            onCaptionDeleted = onCaptionDeleted,
            onPickImage = onPickImage,
            generationHistory = generationHistory,
            activeEntry = activeEntry,
            onSelectFromHistory = onSelectFromHistory,
            onDeleteFromHistory = onDeleteFromHistory,
            onClearAllHistory = onClearAllHistory
        )
    }
}

// Long enough for the 160ms chrome fadeOut tween to settle plus a small
// buffer; see MemeTextOverlay's AnimatedVisibility exit transitions.
private const val CAPTURE_CHROME_FADE_BUFFER_MS = 200L

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 72)
@Composable
private fun BridgeTopBarPreview() {
    MemeMeUpScottyTheme {
        Box(modifier = Modifier.background(Space900)) {
            BridgeTopBar()
        }
    }
}
