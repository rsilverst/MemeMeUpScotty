package com.rsilverst.mememeupscotty.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ErrorOutline
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.window.core.layout.WindowWidthSizeClass
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import com.rsilverst.mememeupscotty.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeScreen(
    viewModel: MainViewModel
) {
    val generationState by viewModel.generationState.collectAsState()
    var hasGeneratedImage by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(generationState) {
        if (generationState is GenerationState.Success) {
            hasGeneratedImage = true
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF5E82F4), Color(0xFF9042F5))
    )

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), color = Color.White) },
                    actions = {
                        // Removed settings button for API key
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            MemeContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                viewModel = viewModel,
                generationState = generationState,
                hasGeneratedImage = hasGeneratedImage,
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
fun MemeContent(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    snackbarHostState: SnackbarHostState
) {
    var prompt by remember { mutableStateOf("") }
    var topText by remember { mutableStateOf("") }
    var bottomText by remember { mutableStateOf("") }

    val selectedModel by viewModel.selectedModel.collectAsState()

    val focusManager = LocalFocusManager.current

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    val graphicsLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var capturing by remember { mutableStateOf(false) }

    val savedToGallery = stringResource(R.string.saved_to_gallery)
    val failedToSave = stringResource(R.string.failed_to_save)
    val storagePermissionRequired = stringResource(R.string.storage_permission_required)

    suspend fun captureCleanBitmap(): android.graphics.Bitmap {
        capturing = true
        // Two frames: first runs recomposition with controls hidden, second ensures the
        // draw pass records the controls-less content into graphicsLayer.
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
                .onSuccess {
                    snackbarHostState.showSnackbar(savedToGallery)
                }
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
            coroutineScope.launch {
                snackbarHostState.showSnackbar(storagePermissionRequired)
            }
        }
    }

    val nothingToSave = stringResource(R.string.nothing_to_save)

    val onSaveAction = {
        focusManager.clearFocus()
        if (generationState !is GenerationState.Success) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(nothingToSave)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                performSave()
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    performSave()
                } else {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    val nothingToShare = stringResource(R.string.nothing_to_share)
    val failedToShare = stringResource(R.string.failed_to_share)

    val onShareAction = {
        focusManager.clearFocus()
        if (generationState !is GenerationState.Success) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(nothingToShare)
            }
        } else {
            coroutineScope.launch {
                val bitmap = captureCleanBitmap()
                shareBitmap(context, bitmap)
                    .onSuccess {
                        // Successfully shared or opened chooser
                    }
                    .onFailure { exception ->
                        val errMsg = exception.localizedMessage ?: exception.message ?: ""
                        snackbarHostState.showSnackbar("$failedToShare: $errMsg")
                    }
            }
        }
    }

    if (isExpanded) {
        Row(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left column: controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PromptInputTextField(
                    value = prompt,
                    onValueChange = { prompt = it }
                )

                ModelSelector(
                    selected = selectedModel,
                    onSelect = { viewModel.selectModel(it) }
                )

                ControlButtons(
                    generationState = generationState,
                    hasGeneratedImage = hasGeneratedImage,
                    onRegenerate = {
                        if (prompt.isNotBlank()) {
                            viewModel.generateImage(prompt, context.cacheDir)
                        }
                    },
                    onSave = { onSaveAction() },
                    onShare = { onShareAction() }
                )
            }

            // Right column: Image
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                MemeImagePreview(
                    generationState = generationState,
                    topText = topText,
                    onTopTextChange = { topText = it },
                    bottomText = bottomText,
                    onBottomTextChange = { bottomText = it },
                    graphicsLayer = graphicsLayer,
                    capturing = capturing
                )
            }
        }
    } else {
        // Compact layout
        Column(
            modifier = modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PromptInputTextField(
                value = prompt,
                onValueChange = { prompt = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModelSelector(
                selected = selectedModel,
                onSelect = { viewModel.selectModel(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                MemeImagePreview(
                    generationState = generationState,
                    topText = topText,
                    onTopTextChange = { topText = it },
                    bottomText = bottomText,
                    onBottomTextChange = { bottomText = it },
                    graphicsLayer = graphicsLayer,
                    capturing = capturing
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ControlButtons(
                generationState = generationState,
                hasGeneratedImage = hasGeneratedImage,
                onRegenerate = {
                    if (prompt.isNotBlank()) {
                        viewModel.generateImage(prompt, context.cacheDir)
                    }
                },
                onSave = { onSaveAction() },
                onShare = { onShareAction() }
            )
            
            // padding for FAB
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selected: ImageModel,
    onSelect: (ImageModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Color.Black
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ImageModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model.label) },
                    onClick = {
                        onSelect(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PromptInputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
        modifier = modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedPlaceholderColor = Color.DarkGray,
            unfocusedPlaceholderColor = Color.DarkGray,
            cursorColor = Color.Black
        )
    )
}

@Composable
fun ControlButtons(
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val isLoading = generationState is GenerationState.Loading
    val generateText = if (hasGeneratedImage || generationState is GenerationState.Success) {
        stringResource(R.string.regenerate)
    } else {
        stringResource(R.string.generate)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onRegenerate,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5CE1E6), contentColor = Color.Black)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(generateText)
        }

        Button(
            onClick = onSave,
            enabled = !isLoading && generationState is GenerationState.Success,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8A2C8), contentColor = Color.Black)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save))
        }

        Button(
            onClick = onShare,
            enabled = !isLoading && generationState is GenerationState.Success,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share))
        }
    }
}

@Composable
fun MemeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    offset: Offset,
    onOffsetChange: (Offset) -> Unit,
    size: Size?,
    onSizeChange: (Size) -> Unit,
    showControls: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val memeFont = remember { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }
    val textMeasurer = rememberTextMeasurer()
    val minWidthPx = with(density) { 60.dp.toPx() }
    val minHeightPx = with(density) { 40.dp.toPx() }
    var currentRenderedSize by remember { mutableStateOf(IntSize.Zero) }

    // pointerInput(Unit) captures these once; State wrappers keep the closures reading the latest values.
    val latestOffset by rememberUpdatedState(offset)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val latestSize by rememberUpdatedState(size)
    val latestOnSizeChange by rememberUpdatedState(onSizeChange)

    val sizeModifier = if (size != null) {
        Modifier.size(
            width = with(density) { size.width.toDp() },
            height = with(density) { size.height.toDp() }
        )
    } else {
        // Default: wrap text content so the box has visible edges to drag against.
        Modifier
    }

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
                cursorBrush = SolidColor(Color.White),
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
                            showControls -> {
                                Text(
                                    text = placeholder,
                                    style = baseStyle.copy(color = Color.White.copy(alpha = 0.45f))
                                )
                            }
                        }
                        innerTextField()
                    }
                }
            )
        }

        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            latestOnOffsetChange(
                                Offset(latestOffset.x + dragAmount.x, latestOffset.y + dragAmount.y)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = stringResource(R.string.move_text_box),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.delete_text_box),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val startW = latestSize?.width ?: currentRenderedSize.width.toFloat()
                            val startH = latestSize?.height ?: currentRenderedSize.height.toFloat()
                            val newW = (startW + dragAmount.x).coerceAtLeast(minWidthPx)
                            val newH = (startH + dragAmount.y).coerceAtLeast(minHeightPx)
                            latestOnSizeChange(Size(newW, newH))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInFull,
                    contentDescription = stringResource(R.string.resize_text_box),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AddTextButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label)
    }
}

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

@Composable
fun MemeImagePreview(
    generationState: GenerationState,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    graphicsLayer: GraphicsLayer,
    capturing: Boolean
) {
    var topVisible by remember { mutableStateOf(true) }
    var bottomVisible by remember { mutableStateOf(true) }
    var topOffset by remember { mutableStateOf(Offset.Zero) }
    var bottomOffset by remember { mutableStateOf(Offset.Zero) }
    var topSize by remember { mutableStateOf<Size?>(null) }
    var bottomSize by remember { mutableStateOf<Size?>(null) }
    val showControls = !capturing

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // Removed dark gray solid background for transparency, better blending with gradient
            .background(Color.Black.copy(alpha = 0.3f))
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
        contentAlignment = Alignment.Center
    ) {
        when (generationState) {
            is GenerationState.Idle -> {
                Text(stringResource(R.string.idle_instruction), color = Color.White)
            }
            is GenerationState.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            is GenerationState.Success -> {
                val file = generationState.imageFile
                AsyncImage(
                    model = file,
                    contentDescription = "Generated Meme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is GenerationState.Error -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = generationState.message,
                            color = Color.White,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Top Text Overlay
        if (topVisible) {
            MemeTextField(
                value = topText,
                onValueChange = onTopTextChange,
                placeholder = stringResource(R.string.default_top_text),
                offset = topOffset,
                onOffsetChange = { topOffset = it },
                size = topSize,
                onSizeChange = { topSize = it },
                showControls = showControls,
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
            AddTextButton(
                onClick = { topVisible = true },
                label = stringResource(R.string.add_top_text),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }

        // Bottom Text Overlay
        if (bottomVisible) {
            MemeTextField(
                value = bottomText,
                onValueChange = onBottomTextChange,
                placeholder = stringResource(R.string.default_bottom_text),
                offset = bottomOffset,
                onOffsetChange = { bottomOffset = it },
                size = bottomSize,
                onSizeChange = { bottomSize = it },
                showControls = showControls,
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
            AddTextButton(
                onClick = { bottomVisible = true },
                label = stringResource(R.string.add_bottom_text),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }
}
