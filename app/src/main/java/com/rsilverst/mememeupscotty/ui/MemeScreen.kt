package com.rsilverst.mememeupscotty.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.MemeCaptionFontFamily
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.theme.Photon500
import com.rsilverst.mememeupscotty.ui.theme.Plasma300
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Plasma700
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Space400
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space600
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space800
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// SCREEN ROOT
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeScreen(
    viewModel: MainViewModel
) {
    val generationState by viewModel.generationState.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    var hasGeneratedImage by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(generationState) {
        if (generationState is GenerationState.Success) {
            hasGeneratedImage = true
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
                generationState = generationState,
                selectedModel = selectedModel,
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
            Color(0xFF0D1128),
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
// ADAPTIVE LAYOUT
// ============================================================================

@Composable
private fun MemeContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenModelPicker: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var topText by remember { mutableStateOf("") }
    var bottomText by remember { mutableStateOf("") }

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

    suspend fun captureCleanBitmap(): android.graphics.Bitmap {
        capturing = true
        withFrameNanos { }
        withFrameNanos { }
        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
        capturing = false
        return bitmap
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

    val onPromptChipClick: (String) -> Unit = { suggestion ->
        prompt = suggestion
    }

    if (isExpanded) {
        ExpandedLayout(
            modifier = modifier,
            prompt = prompt,
            onPromptChange = { prompt = it },
            topText = topText,
            onTopTextChange = { topText = it },
            bottomText = bottomText,
            onBottomTextChange = { bottomText = it },
            generationState = generationState,
            selectedModel = selectedModel,
            hasGeneratedImage = hasGeneratedImage,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onOpenModelPicker = onOpenModelPicker,
            onEnergize = onEnergize,
            onSave = onSaveAction,
            onShare = onShareAction,
            onPromptChip = onPromptChipClick
        )
    } else {
        CompactLayout(
            modifier = modifier,
            prompt = prompt,
            onPromptChange = { prompt = it },
            topText = topText,
            onTopTextChange = { topText = it },
            bottomText = bottomText,
            onBottomTextChange = { bottomText = it },
            generationState = generationState,
            selectedModel = selectedModel,
            hasGeneratedImage = hasGeneratedImage,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onOpenModelPicker = onOpenModelPicker,
            onEnergize = onEnergize,
            onSave = onSaveAction,
            onShare = onShareAction,
            onPromptChip = onPromptChipClick
        )
    }
}

// ============================================================================
// COMPACT (PHONE) LAYOUT
// ============================================================================

@Composable
private fun CompactLayout(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        MemeCanvas(
            generationState = generationState,
            topText = topText,
            onTopTextChange = onTopTextChange,
            bottomText = bottomText,
            onBottomTextChange = onBottomTextChange,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onPromptChip = onPromptChip,
            onRetry = onEnergize
        )

        Spacer(modifier = Modifier.height(12.dp))

        HudStrip(
            selectedModel = selectedModel,
            generationState = generationState,
            onOpenModelPicker = onOpenModelPicker,
            onReroll = onEnergize
        )

        Spacer(modifier = Modifier.height(20.dp))

        Dock(
            prompt = prompt,
            onPromptChange = onPromptChange,
            generationState = generationState,
            hasGeneratedImage = hasGeneratedImage,
            onEnergize = onEnergize,
            onSave = onSave,
            onShare = onShare
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ============================================================================
// EXPANDED (TABLET) LAYOUT
// ============================================================================

@Composable
private fun ExpandedLayout(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
        ) {
            MemeCanvas(
                generationState = generationState,
                topText = topText,
                onTopTextChange = onTopTextChange,
                bottomText = bottomText,
                onBottomTextChange = onBottomTextChange,
                capturing = capturing,
                graphicsLayer = graphicsLayer,
                onPromptChip = onPromptChip,
                onRetry = onEnergize,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(12.dp))
            HudStrip(
                selectedModel = selectedModel,
                generationState = generationState,
                onOpenModelPicker = onOpenModelPicker,
                onReroll = onEnergize
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FieldLabel(text = "PROMPT")
            PromptInput(
                value = prompt,
                onValueChange = onPromptChange,
                singleLine = false
            )

            FieldLabel(text = "ENGINE")
            ModelSelectorButton(
                model = selectedModel,
                onClick = onOpenModelPicker
            )

            Spacer(Modifier.height(4.dp))

            EnergizeButton(
                generationState = generationState,
                hasGeneratedImage = hasGeneratedImage,
                onClick = onEnergize
            )

            val isLoading = generationState is GenerationState.Loading
            val hasImage = generationState is GenerationState.Success
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GhostButton(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.save),
                    enabled = !isLoading && hasImage,
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    icon = Icons.Filled.IosShare,
                    label = stringResource(R.string.share),
                    enabled = !isLoading && hasImage,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextLow,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ============================================================================
// CANVAS — holds image, captions, and all canvas-level states (idle/loading/error)
// ============================================================================

@Composable
private fun MemeCanvas(
    generationState: GenerationState,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onPromptChip: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var topVisible by remember { mutableStateOf(true) }
    var bottomVisible by remember { mutableStateOf(true) }
    var topOffset by remember { mutableStateOf(Offset.Zero) }
    var bottomOffset by remember { mutableStateOf(Offset.Zero) }
    var topSize by remember { mutableStateOf<Size?>(null) }
    var bottomSize by remember { mutableStateOf<Size?>(null) }
    val showControls = !capturing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Space700)
            .border(1.dp, Space500, RoundedCornerShape(20.dp))
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
        contentAlignment = Alignment.Center
    ) {
        when (generationState) {
            is GenerationState.Idle    -> EmptyState(onPromptChip = onPromptChip)
            is GenerationState.Loading -> LoadingState()
            is GenerationState.Success -> {
                AsyncImage(
                    model = generationState.imageFile,
                    contentDescription = "Generated Meme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is GenerationState.Error   -> ErrorState(
                message = generationState.message,
                onRetry = onRetry
            )
        }

        // Captions only make sense once there's an image to put them on.
        if (generationState is GenerationState.Success) {
            if (topVisible) {
                MemeTextOverlay(
                    value = topText,
                    onValueChange = onTopTextChange,
                    placeholder = stringResource(R.string.default_top_text),
                    offset = topOffset,
                    onOffsetChange = { topOffset = it },
                    size = topSize,
                    onSizeChange = { topSize = it },
                    capturing = capturing,
                    onDelete = {
                        topVisible = false
                        topOffset = Offset.Zero
                        topSize = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                )
            } else if (showControls) {
                AddTextPill(
                    onClick = { topVisible = true },
                    label = stringResource(R.string.add_top_text),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }

            if (bottomVisible) {
                MemeTextOverlay(
                    value = bottomText,
                    onValueChange = onBottomTextChange,
                    placeholder = stringResource(R.string.default_bottom_text),
                    offset = bottomOffset,
                    onOffsetChange = { bottomOffset = it },
                    size = bottomSize,
                    onSizeChange = { bottomSize = it },
                    capturing = capturing,
                    onDelete = {
                        bottomVisible = false
                        bottomOffset = Offset.Zero
                        bottomSize = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                )
            } else if (showControls) {
                AddTextPill(
                    onClick = { bottomVisible = true },
                    label = stringResource(R.string.add_bottom_text),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

// ============================================================================
// CANVAS STATES
// ============================================================================

@Composable
private fun EmptyState(onPromptChip: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TransporterPad(modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.idle_instruction),
            style = MaterialTheme.typography.titleLarge,
            color = TextHigh,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.idle_try_one).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextLow
        )
        Spacer(Modifier.height(12.dp))
        val chips = listOf(
            stringResource(R.string.suggested_prompt_1),
            stringResource(R.string.suggested_prompt_2),
            stringResource(R.string.suggested_prompt_3)
        )
        chips.forEach { chip ->
            PromptChip(
                label = chip,
                onClick = { onPromptChip(chip) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PromptChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.04f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Space500),
        contentColor = TextMid
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TransporterPad(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val baseY = h * 0.82f

        // Outer pad ellipse
        drawOval(
            color = Plasma500.copy(alpha = 0.15f),
            topLeft = Offset(w * 0.05f, baseY - h * 0.08f),
            size = Size(w * 0.9f, h * 0.16f)
        )
        drawOval(
            color = Color.Transparent,
            topLeft = Offset(w * 0.05f, baseY - h * 0.08f),
            size = Size(w * 0.9f, h * 0.16f),
            style = Stroke(width = 1.dp.toPx())
        )

        // Inner pad ellipse
        drawOval(
            color = Plasma500.copy(alpha = 0.30f),
            topLeft = Offset(w * 0.22f, baseY - h * 0.05f),
            size = Size(w * 0.56f, h * 0.10f)
        )

        // Vertical beam lines
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
        val beamHeights = listOf(0.10f to 0.6f, 0.18f to 0.9f, 0.10f to 0.6f)
        val xs = listOf(centerX - w * 0.30f, centerX, centerX + w * 0.30f)
        xs.zip(beamHeights).forEach { (x, top) ->
            drawLine(
                color = Plasma500.copy(alpha = top.second),
                start = Offset(x, h * top.first),
                end = Offset(x, baseY - h * 0.02f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        // Center beam dot
        drawCircle(
            color = Plasma300,
            radius = 3.dp.toPx(),
            center = Offset(centerX, baseY - h * 0.02f)
        )
    }
}

@Composable
private fun LoadingState() {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val scanOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Space800)
            .drawBehind {
                // Scan lines
                val lineSpacing = 14.dp.toPx()
                val offsetY = scanOffset * lineSpacing
                var y = -lineSpacing + offsetY
                while (y < size.height) {
                    drawLine(
                        color = Plasma500.copy(alpha = 0.18f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Plasma500.copy(alpha = 0.06f),
                        start = Offset(0f, y + 1.dp.toPx()),
                        end = Offset(size.width, y + 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineSpacing
                }
                // Particles — fixed positions, breathing alpha
                val particles = listOf(
                    Offset(size.width * 0.20f, size.height * 0.30f) to 1.5.dp.toPx(),
                    Offset(size.width * 0.80f, size.height * 0.50f) to 1.2.dp.toPx(),
                    Offset(size.width * 0.50f, size.height * 0.70f) to 2.dp.toPx(),
                    Offset(size.width * 0.35f, size.height * 0.85f) to 1.dp.toPx(),
                    Offset(size.width * 0.70f, size.height * 0.20f) to 1.5.dp.toPx(),
                    Offset(size.width * 0.10f, size.height * 0.60f) to 1.dp.toPx(),
                    Offset(size.width * 0.92f, size.height * 0.85f) to 1.2.dp.toPx(),
                    Offset(size.width * 0.45f, size.height * 0.15f) to 1.dp.toPx()
                )
                particles.forEachIndexed { i, (pos, r) ->
                    val phaseAlpha = (0.4f + 0.6f * (0.5f + 0.5f * sin((scanOffset * Math.PI * 2 + i).toFloat())))
                    drawCircle(
                        color = if (i % 2 == 0) Plasma500 else Plasma300,
                        radius = r,
                        center = pos,
                        alpha = phaseAlpha
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = Plasma500.copy(alpha = pulseAlpha),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.materializing).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp),
                color = Plasma300
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val themedTitle = themedErrorTitle(message)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Space700)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Red500.copy(alpha = 0.1f))
                .border(1.dp, Red500.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = Red500
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = themedTitle,
            style = MaterialTheme.typography.titleLarge,
            color = TextHigh,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            color = Plasma500,
            contentColor = Space900
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.error_try_again).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
                )
            }
        }
    }
}

@Composable
private fun themedErrorTitle(message: String): String {
    val lower = message.lowercase()
    return when {
        "token" in lower || "401" in lower         -> stringResource(R.string.error_title_auth)
        "credit" in lower || "billing" in lower    -> stringResource(R.string.error_title_quota)
        "isn't available" in lower || "404" in lower -> stringResource(R.string.error_title_not_found)
        "rate limit" in lower || "429" in lower    -> stringResource(R.string.error_title_rate_limit)
        "having a problem" in lower || "http 5" in lower -> stringResource(R.string.error_title_server)
        else -> stringResource(R.string.error_title_generic)
    }
}

// ============================================================================
// MEME TEXT OVERLAY — selection chrome appears on focus
// ============================================================================

@Composable
private fun MemeTextOverlay(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    size: Size?,
    onSizeChange: (Size) -> Unit,
    capturing: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val memeFont = MemeCaptionFontFamily
    val textMeasurer = rememberTextMeasurer()
    val minWidthPx = with(density) { 60.dp.toPx() }
    val minHeightPx = with(density) { 40.dp.toPx() }
    var currentRenderedSize by remember { mutableStateOf(IntSize.Zero) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val latestOffset by rememberUpdatedState(offset)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val latestSize by rememberUpdatedState(size)
    val latestOnSizeChange by rememberUpdatedState(onSizeChange)

    val showChrome = hasFocus && !capturing

    val sizeModifier = if (size != null) {
        Modifier.size(
            width = with(density) { size.width.toDp() },
            height = with(density) { size.height.toDp() }
        )
    } else Modifier

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .then(sizeModifier)
            .onSizeChanged { currentRenderedSize = it }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestOnOffsetChange(
                        Offset(latestOffset.x + dragAmount.x, latestOffset.y + dragAmount.y)
                    )
                }
            }
            .drawBehind {
                if (showChrome) {
                    val pad = 8.dp.toPx()
                    drawRoundRect(
                        color = Plasma500,
                        topLeft = Offset(-pad, -pad),
                        size = Size(this.size.width + pad * 2, this.size.height + pad * 2),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints {
            val maxWidthPx = constraints.maxWidth
            val maxHeightPx = size?.height?.toInt()
                ?: with(density) { 96.dp.toPx() }.toInt()
            val displayText = value.ifEmpty { placeholder }
            val fontSize = remember(displayText, maxWidthPx, maxHeightPx) {
                findBestFitFontSize(displayText, textMeasurer, memeFont, maxWidthPx, maxHeightPx)
            }
            val strokeWidthPx = with(density) { (fontSize.value * 0.15f).sp.toPx() }
            val baseStyle = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                fontFamily = memeFont,
                textAlign = TextAlign.Center
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = baseStyle.copy(color = Color.White),
                cursorBrush = SolidColor(Plasma500),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { hasFocus = it.isFocused },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            value.isNotEmpty() -> {
                                Text(
                                    text = value,
                                    style = baseStyle.copy(
                                        color = Color.Black,
                                        drawStyle = Stroke(
                                            width = strokeWidthPx,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                )
                            }
                            !capturing -> {
                                Text(
                                    text = placeholder,
                                    style = baseStyle.copy(
                                        color = Color.White.copy(alpha = if (hasFocus) 0.55f else 0.30f)
                                    )
                                )
                            }
                        }
                        innerTextField()
                    }
                }
            )
        }

        // Selection chrome: floating delete chip + resize handle
        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            ResizeHandle(
                onDrag = { dragAmount ->
                    val startW = latestSize?.width ?: currentRenderedSize.width.toFloat()
                    val startH = latestSize?.height ?: currentRenderedSize.height.toFloat()
                    val newW = (startW + dragAmount.x).coerceAtLeast(minWidthPx)
                    val newH = (startH + dragAmount.y).coerceAtLeast(minHeightPx)
                    latestOnSizeChange(Size(newW, newH))
                }
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            DeleteChip(onClick = onDelete)
        }
    }
}

@Composable
private fun ResizeHandle(onDrag: (Offset) -> Unit) {
    Box(
        modifier = Modifier
            .offset(x = 18.dp, y = 18.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Plasma500)
            .border(1.dp, Plasma300, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInFull,
            contentDescription = stringResource(R.string.resize_text_box),
            tint = Space900,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DeleteChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Space600,
        contentColor = Red500,
        border = androidx.compose.foundation.BorderStroke(1.dp, Space500),
        modifier = Modifier.offset(x = 18.dp, y = (-18).dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.delete_text_box),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddTextPill(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Space700.copy(alpha = 0.85f),
        contentColor = Plasma500,
        border = androidx.compose.foundation.BorderStroke(1.dp, Plasma700),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// ============================================================================
// HUD STRIP
// ============================================================================

@Composable
private fun HudStrip(
    selectedModel: ImageModel,
    generationState: GenerationState,
    onOpenModelPicker: () -> Unit,
    onReroll: () -> Unit
) {
    val isLoading = generationState is GenerationState.Loading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Space600.copy(alpha = 0.6f))
            .border(1.dp, Space500, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenModelPicker() }) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Plasma500.copy(alpha = 0.12f))
                    .border(1.dp, Plasma500.copy(alpha = 0.30f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedModel.shortGlyph,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = Plasma500
                )
            }
            Text(
                text = selectedModel.shortLabel,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.2.sp),
                color = TextHigh
            )
            Text(
                text = "·",
                color = Space400,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "tap to change",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow
            )
        }

        HudIconButton(
            icon = Icons.Filled.Casino,
            contentDescription = stringResource(R.string.hud_reroll),
            enabled = !isLoading,
            onClick = onReroll
        )
    }
}

@Composable
private fun HudIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.04f),
        contentColor = if (enabled) TextMid else TextLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, Space500),
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================================================
// DOCK — sticky bottom prompt + actions
// ============================================================================

@Composable
private fun Dock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PromptInput(
            value = prompt,
            onValueChange = onPromptChange,
            singleLine = false
        )

        EnergizeButton(
            generationState = generationState,
            hasGeneratedImage = hasGeneratedImage,
            onClick = onEnergize
        )

        val isLoading = generationState is GenerationState.Loading
        val hasImage = generationState is GenerationState.Success
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GhostButton(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.save),
                enabled = !isLoading && hasImage,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
            GhostButton(
                icon = Icons.Filled.IosShare,
                label = stringResource(R.string.share),
                enabled = !isLoading && hasImage,
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Plasma500 else Space500

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Space700)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = TextLow,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 1.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextHigh),
            cursorBrush = SolidColor(Plasma500),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun EnergizeButton(
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    onClick: () -> Unit
) {
    val isLoading = generationState is GenerationState.Loading
    val label = when {
        isLoading -> stringResource(R.string.generating)
        hasGeneratedImage || generationState is GenerationState.Success ->
            stringResource(R.string.regenerate)
        else -> stringResource(R.string.generate)
    }
    val containerColor = if (isLoading) Space600 else Plasma500
    val contentColor = if (isLoading) Plasma500 else Space900

    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (!isLoading) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun GhostButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (enabled) TextMid else TextMid.copy(alpha = 0.4f)
    val borderColor = if (enabled) Space500 else Space500.copy(alpha = 0.5f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
            )
        }
    }
}

// ============================================================================
// MODEL SELECTOR (tablet expanded view) + MODEL PICKER SHEET
// ============================================================================

@Composable
private fun ModelSelectorButton(
    model: ImageModel,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Space700,
        contentColor = TextHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, Space500),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Plasma500.copy(alpha = 0.12f))
                    .border(1.dp, Plasma500.copy(alpha = 0.30f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = model.shortGlyph,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = Plasma500
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = model.shortLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = TextLow
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    selected: ImageModel,
    onSelect: (ImageModel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Space700,
        scrimColor = Space900.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Space500)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.choose_engine),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextHigh,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))

            ImageModel.entries.forEach { model ->
                ModelCard(
                    model = model,
                    isSelected = model == selected,
                    onClick = { onSelect(model) }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ImageModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Plasma700 else Space500
    val bg = if (isSelected) Space600.copy(alpha = 0.6f) else Space800

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Glyph
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Plasma500.copy(alpha = 0.10f) else Space700
                    )
                    .border(
                        1.dp,
                        if (isSelected) Plasma500.copy(alpha = 0.30f) else Space500,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = model.shortGlyph,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        letterSpacing = 0.sp
                    ),
                    color = if (isSelected) Plasma500 else TextMid
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(model.displayNameRes),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        color = TextHigh,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Plasma500.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Plasma500,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.model_active).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                color = Plasma500
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(model.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
            }
        }
    }
}

// ============================================================================
// MODEL METADATA EXTENSIONS — title-case short label + glyph + description
// ============================================================================

private val ImageModel.shortLabel: String
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> "Juggernaut"
        ImageModel.STABILITY    -> "Stability"
        ImageModel.REALVIS      -> "RealVis"
        ImageModel.FLUX_SCHNELL -> "Flux Schnell"
        ImageModel.DREAMSHAPER  -> "DreamShaper"
        ImageModel.BLUE_PENCIL  -> "Blue Pencil"
        ImageModel.PROTEUS      -> "Proteus"
    }

private val ImageModel.shortGlyph: String
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> "J"
        ImageModel.STABILITY    -> "S"
        ImageModel.REALVIS      -> "R"
        ImageModel.FLUX_SCHNELL -> "F"
        ImageModel.DREAMSHAPER  -> "D"
        ImageModel.BLUE_PENCIL  -> "B"
        ImageModel.PROTEUS      -> "P"
    }

private val ImageModel.displayNameRes: Int
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> R.string.model_juggernaut_name
        ImageModel.STABILITY    -> R.string.model_stability_name
        ImageModel.REALVIS      -> R.string.model_realvis_name
        ImageModel.FLUX_SCHNELL -> R.string.model_flux_name
        ImageModel.DREAMSHAPER  -> R.string.model_dreamshaper_name
        ImageModel.BLUE_PENCIL  -> R.string.model_bluepencil_name
        ImageModel.PROTEUS      -> R.string.model_proteus_name
    }

private val ImageModel.descriptionRes: Int
    get() = when (this) {
        ImageModel.JUGGERNAUT   -> R.string.model_juggernaut_desc
        ImageModel.STABILITY    -> R.string.model_stability_desc
        ImageModel.REALVIS      -> R.string.model_realvis_desc
        ImageModel.FLUX_SCHNELL -> R.string.model_flux_desc
        ImageModel.DREAMSHAPER  -> R.string.model_dreamshaper_desc
        ImageModel.BLUE_PENCIL  -> R.string.model_bluepencil_desc
        ImageModel.PROTEUS      -> R.string.model_proteus_desc
    }

// ============================================================================
// MEME CAPTION FONT FITTING — preserved from original
// ============================================================================

private fun findBestFitFontSize(
    text: String,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    maxWidthPx: Int,
    maxHeightPx: Int
): TextUnit {
    if (text.isEmpty() || maxWidthPx <= 0) return 40.sp
    val widthConstraints = Constraints(maxWidth = maxWidthPx)
    for (sp in 40 downTo 14 step 2) {
        val layout = textMeasurer.measure(
            text = text,
            style = TextStyle(
                fontSize = sp.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            ),
            constraints = widthConstraints
        )
        if (layout.size.height <= maxHeightPx) return sp.sp
    }
    return 14.sp
}

// ============================================================================
// PREVIEWS
// All previews are dark-on-Space900 to match the app's runtime appearance.
// ============================================================================

private const val PREVIEW_BG = 0xFF0B0E1FL

@Composable
private fun PreviewShell(
    width: Int = 360,
    content: @Composable () -> Unit
) {
    MemeMeUpScottyTheme {
        Box(
            modifier = Modifier
                .background(Space900)
                .padding(16.dp)
                .width(width.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PreviewCanvasFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(328.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Space700)
            .border(1.dp, Space500, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 72)
@Composable
private fun BridgeTopBarPreview() {
    MemeMeUpScottyTheme {
        Box(modifier = Modifier.background(Space900)) {
            BridgeTopBar()
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasEmptyStatePreview() {
    PreviewShell {
        PreviewCanvasFrame {
            EmptyState(onPromptChip = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasLoadingStatePreview() {
    PreviewShell {
        PreviewCanvasFrame {
            LoadingState()
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasErrorStatePreview_Quota() {
    PreviewShell {
        PreviewCanvasFrame {
            ErrorState(
                message = "You're out of Replicate credit. Add some at replicate.com/account/billing.",
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasErrorStatePreview_Auth() {
    PreviewShell {
        PreviewCanvasFrame {
            ErrorState(
                message = "Your Replicate API token isn't accepted. Check REPLICATE_API_TOKEN in local.properties.",
                onRetry = {}
            )
        }
    }
}

// Fake "image" — radial gradient stand-in so we can preview captions over content.
@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasSuccessWithCaptionsPreview() {
    PreviewShell {
        Box(
            modifier = Modifier
                .size(328.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3A2A5C),
                            Color(0xFF1A1F3E),
                            Color(0xFF0F1024)
                        )
                    )
                )
                .border(1.dp, Space500, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "I CAN HAS",
                fontFamily = MemeCaptionFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
            )
            Text(
                text = "WARP DRIVE?",
                fontFamily = MemeCaptionFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 80)
@Composable
private fun HudStripPreview() {
    PreviewShell {
        HudStrip(
            selectedModel = ImageModel.JUGGERNAUT,
            generationState = GenerationState.Success(java.io.File("")),
            onOpenModelPicker = {},
            onReroll = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Generate() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Idle,
            hasGeneratedImage = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Regenerate() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Success(java.io.File("")),
            hasGeneratedImage = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Loading() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Loading,
            hasGeneratedImage = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 110)
@Composable
private fun ModelCardPreview_Selected() {
    PreviewShell {
        ModelCard(
            model = ImageModel.JUGGERNAUT,
            isSelected = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 110)
@Composable
private fun ModelCardPreview_Unselected() {
    PreviewShell {
        ModelCard(
            model = ImageModel.FLUX_SCHNELL,
            isSelected = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun PromptInputPreview_Empty() {
    PreviewShell {
        PromptInput(value = "", onValueChange = {}, singleLine = true)
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun PromptInputPreview_Filled() {
    PreviewShell {
        PromptInput(
            value = "a corgi as the captain of the enterprise",
            onValueChange = {},
            singleLine = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 280)
@Composable
private fun DockPreview_NoImage() {
    PreviewShell {
        Dock(
            prompt = "",
            onPromptChange = {},
            generationState = GenerationState.Idle,
            hasGeneratedImage = false,
            onEnergize = {},
            onSave = {},
            onShare = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 280)
@Composable
private fun DockPreview_WithImage() {
    PreviewShell {
        Dock(
            prompt = "a corgi as the captain",
            onPromptChange = {},
            generationState = GenerationState.Success(java.io.File("")),
            hasGeneratedImage = true,
            onEnergize = {},
            onSave = {},
            onShare = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 80)
@Composable
private fun ModelSelectorButtonPreview() {
    PreviewShell {
        ModelSelectorButton(
            model = ImageModel.REALVIS,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 80)
@Composable
private fun AddTextPillPreview() {
    PreviewShell {
        AddTextPill(
            onClick = {},
            label = "Add top text"
        )
    }
}
