package com.rsilverst.mememeupscotty.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.ui.theme.MemeCaptionFontFamily
import com.rsilverst.mememeupscotty.ui.theme.Plasma300
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Photon500
import com.rsilverst.mememeupscotty.ui.theme.Solar500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space800
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import kotlinx.coroutines.delay
import kotlin.math.sin

// Square canvas containing the active state (idle / loading / image / error)
// plus the two caption overlays. Records itself into the supplied graphics
// layer on every recomposition so bitmap capture (Save / Share) is one
// `graphicsLayer.toImageBitmap()` call away.

@Composable
internal fun MemeCanvas(
    generationState: GenerationState,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onPromptChip: (String) -> Unit,
    onRetry: () -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit = {},
    onPickImage: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var topVisible by remember { mutableStateOf(true) }
    var bottomVisible by remember { mutableStateOf(true) }
    var topOffset by remember { mutableStateOf(Offset.Zero) }
    var bottomOffset by remember { mutableStateOf(Offset.Zero) }
    var topSize by remember { mutableStateOf<Size?>(null) }
    var bottomSize by remember { mutableStateOf<Size?>(null) }
    var topStyle by remember { mutableStateOf(CaptionStyle()) }
    var bottomStyle by remember { mutableStateOf(CaptionStyle()) }
    var styleSheetTarget by remember { mutableStateOf<CaptionTarget?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val showControls = !capturing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Space700)
            .border(1.dp, Space500, RoundedCornerShape(20.dp))
            .onSizeChanged { canvasSize = it }
            // Record into the graphics layer only while capturing so Save /
            // Share can read a snapshot via graphicsLayer.toImageBitmap().
            // During normal use we just drawContent() directly — recording
            // every frame doubled draw work for no benefit.
            .drawWithContent {
                if (capturing) {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(graphicsLayer)
                } else {
                    drawContent()
                }
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
                error = generationState.error,
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
                    style = topStyle,
                    capturing = capturing,
                    parentSize = canvasSize,
                    onDelete = {
                        val restoreOffset = topOffset
                        val restoreSize = topSize
                        topVisible = false
                        topOffset = Offset.Zero
                        topSize = null
                        onCaptionDeleted {
                            topVisible = true
                            topOffset = restoreOffset
                            topSize = restoreSize
                        }
                    },
                    onOpenStyleSheet = { styleSheetTarget = CaptionTarget.TOP },
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
                    style = bottomStyle,
                    capturing = capturing,
                    parentSize = canvasSize,
                    onDelete = {
                        val restoreOffset = bottomOffset
                        val restoreSize = bottomSize
                        bottomVisible = false
                        bottomOffset = Offset.Zero
                        bottomSize = null
                        onCaptionDeleted {
                            bottomVisible = true
                            bottomOffset = restoreOffset
                            bottomSize = restoreSize
                        }
                    },
                    onOpenStyleSheet = { styleSheetTarget = CaptionTarget.BOTTOM },
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

        // Persistent "Use a photo" affordance pinned to the canvas
        // top-right. Visible in every canvas state — idle (primary
        // discoverability), loading, success (acts as "replace"), and
        // error — and fades only during capture so it never leaks into
        // a saved/shared bitmap.
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            PhotoSourceChip(onClick = onPickImage)
        }
    }

    when (styleSheetTarget) {
        CaptionTarget.TOP -> CaptionStyleSheet(
            style = topStyle,
            onStyleChange = { topStyle = it },
            onDismiss = { styleSheetTarget = null }
        )
        CaptionTarget.BOTTOM -> CaptionStyleSheet(
            style = bottomStyle,
            onStyleChange = { bottomStyle = it },
            onDismiss = { styleSheetTarget = null }
        )
        null -> Unit
    }
}

private enum class CaptionTarget { TOP, BOTTOM }

// ============================================================================
// Canvas states
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
        chips.forEachIndexed { index, chip ->
            val color = when (index) {
                0 -> Plasma500
                1 -> Photon500
                else -> Solar500
            }
            PromptChip(
                label = chip,
                borderColor = color.copy(alpha = 0.40f),
                textColor = color.copy(alpha = 0.90f),
                backgroundColor = color.copy(alpha = 0.06f),
                onClick = { onPromptChip(chip) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// Persistent source-picker chip pinned to the canvas top-right corner.
// Labeled (icon + text) so its purpose is obvious in every state — not
// just an unmarked overlay glyph. Semi-opaque dark fill so it reads
// against any generated image without depending on the underlying hue.
@Composable
private fun PhotoSourceChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Space900.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Plasma500.copy(alpha = 0.40f)),
        contentColor = TextHigh,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Plasma500,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.use_photo),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PromptChip(
    label: String,
    borderColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        contentColor = textColor
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
    // Elapsed-second counter so the user has some sense of progress on slow
    // models. Restarts whenever LoadingState re-enters composition (i.e. a
    // new generation begins).
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSec++
        }
    }

    val infinite = rememberInfiniteTransition(label = "shimmer")
    val scanOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 1700ms reads as a calm "computing" rhythm without feeling sluggish;
            // 2200ms (the original) edges on slow when you're staring at it.
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )
    // Drives both bolt alpha (subtle 0.8 → 1.0 breath) and color (Plasma500 →
    // Solar500), so the bolt warms into gold at the peak per the mockup.
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val boltColor = lerp(Plasma500, Solar500, pulse)
    val boltAlpha = 0.8f + 0.2f * pulse

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
                tint = boltColor.copy(alpha = boltAlpha),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.materializing).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp),
                color = Plasma300
            )
            if (elapsedSec > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "T+${elapsedSec}S",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow
                )
            }
        }
    }
}

@Composable
private fun ErrorState(error: GenerationError, onRetry: () -> Unit) {
    val title = stringResource(error.titleRes())
    val detail = error.detailText()
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextHigh,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
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

// GenerationError → headline resource. Pure when-mapping; no string
// keyword matching. Adding a variant requires updating this and
// detailText() below — the compiler will flag the missing case.
private fun GenerationError.titleRes(): Int = when (this) {
    GenerationError.AuthRejected      -> R.string.error_title_auth
    GenerationError.OutOfCredit       -> R.string.error_title_quota
    GenerationError.ModelUnavailable  -> R.string.error_title_not_found
    is GenerationError.RateLimited    -> R.string.error_title_rate_limit
    is GenerationError.Server         -> R.string.error_title_server
    GenerationError.Timeout           -> R.string.error_title_timeout
    is GenerationError.Unexpected     -> R.string.error_title_generic
}

// GenerationError → detail body text. Wraps Compose's stringResource so
// it can be called from the @Composable ErrorState; for variants with
// parameters (RateLimited's retry seconds, Server's HTTP code) we use
// the templated resource.
@Composable
private fun GenerationError.detailText(): String = when (this) {
    GenerationError.AuthRejected     -> stringResource(R.string.error_detail_auth)
    GenerationError.OutOfCredit      -> stringResource(R.string.error_detail_quota)
    GenerationError.ModelUnavailable -> stringResource(R.string.error_detail_not_found)
    is GenerationError.RateLimited   -> retryAfterSec?.let {
        stringResource(R.string.error_detail_rate_limit_retry, it)
    } ?: stringResource(R.string.error_detail_rate_limit_no_retry)
    is GenerationError.Server        -> stringResource(R.string.error_detail_server, httpCode)
    GenerationError.Timeout          -> stringResource(R.string.error_detail_timeout)
    is GenerationError.Unexpected    -> detail
}

// ============================================================================
// Previews
// ============================================================================

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
                error = GenerationError.OutOfCredit,
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
                error = GenerationError.AuthRejected,
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasErrorStatePreview_RateLimited() {
    PreviewShell {
        PreviewCanvasFrame {
            ErrorState(
                error = GenerationError.RateLimited(retryAfterSec = 30),
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 360)
@Composable
private fun CanvasErrorStatePreview_Timeout() {
    PreviewShell {
        PreviewCanvasFrame {
            ErrorState(
                error = GenerationError.Timeout,
                onRetry = {}
            )
        }
    }
}

// Fake "image" — radial gradient stand-in so captions can be previewed
// over picture-like content.
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
