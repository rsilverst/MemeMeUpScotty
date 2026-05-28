package com.rsilverst.mememeupscotty.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ErrorOutline
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val savedToGallery = stringResource(R.string.saved_to_gallery)
    val failedToSave = stringResource(R.string.failed_to_save)
    val storagePermissionRequired = stringResource(R.string.storage_permission_required)

    val performSave = {
        coroutineScope.launch {
            val bitmap = if (generationState is GenerationState.Success) {
                val file = generationState.imageFile
                val baseBitmap = withContext(Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }
                if (baseBitmap != null) {
                    withContext(Dispatchers.Default) {
                        generateHighResMeme(baseBitmap, topText, bottomText)
                    }
                } else {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }
            } else {
                graphicsLayer.toImageBitmap().asAndroidBitmap()
            }
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
                val file = generationState.imageFile
                val baseBitmap = withContext(Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }
                val bitmap = if (baseBitmap != null) {
                    withContext(Dispatchers.Default) {
                        generateHighResMeme(baseBitmap, topText, bottomText)
                    }
                } else {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }
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
                    graphicsLayer = graphicsLayer
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
                    graphicsLayer = graphicsLayer
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokeWidth = remember(density) { with(density) { 6.sp.toPx() } }
    val memeFont = remember { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            fontFamily = memeFont,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(Color.White),
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Background outline text
                Text(
                    text = value,
                    style = TextStyle(
                        color = Color.Black,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = memeFont,
                        textAlign = TextAlign.Center,
                        drawStyle = Stroke(
                            width = strokeWidth,
                            join = StrokeJoin.Round
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                innerTextField()
            }
        },
        modifier = modifier
    )
}

@Composable
fun MemeImagePreview(
    generationState: GenerationState,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    graphicsLayer: GraphicsLayer
) {
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
        MemeTextField(
            value = topText,
            onValueChange = onTopTextChange,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        )

        // Bottom Text Overlay
        MemeTextField(
            value = bottomText,
            onValueChange = onBottomTextChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        )
    }
}
