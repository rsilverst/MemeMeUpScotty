package com.rsilverst.mememeupscotty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.Photon500
import com.rsilverst.mememeupscotty.ui.theme.Plasma300
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Plasma700
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Solar500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space800
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionSnapshot
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.data.repository.GenerationError
import com.rsilverst.mememeupscotty.ui.viewmodel.HistoryEntry
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

// The main visual area showing the active image and captions, or fallback
// states (empty, loading, error) if nothing is loaded. It also handles capturing
// a snapshot of its content into a GraphicsLayer for Save/Share.

@Composable
internal fun MemeCanvas(
    generationState: GenerationState,
    activeEntry: HistoryEntry?,
    captions: CaptionSnapshot,
    onCaptionsChange: ((CaptionSnapshot) -> CaptionSnapshot) -> Unit,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onPromptChip: (String) -> Unit,
    onRetry: () -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val top = captions.top
    val bottom = captions.bottom
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // If the canvas width changed (e.g. device rotation, split screen), record
    // it in the snapshot. The TextOverlays need this reference width so their
    // internal wrap logic (how many characters fit per line) remains identical
    // to what the HistoryStrip will compute for its scaled thumbnails.
    val editCaptions: ((CaptionSnapshot) -> CaptionSnapshot) -> Unit = { transform ->
        onCaptionsChange { snap ->
            val stamped = if (canvasSize.width > 0) snap.copy(refSize = canvasSize.width.toFloat()) else snap
            transform(stamped)
        }
    }

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
        val displayedFile = activeEntry?.file
        if (displayedFile != null) {
            AsyncImage(
                model = displayedFile,
                contentDescription = "Generated Meme",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Full screen loaders / empty states when no image is loaded
            when (generationState) {
                is GenerationState.Loading -> LoadingState()
                is GenerationState.Error   -> ErrorState(
                    error = generationState.error,
                    onRetry = onRetry
                )
                else -> EmptyState(onPromptChip = onPromptChip)
            }
        }

        // Captions only make sense once there's an image to put them on.
        if (displayedFile != null) {
            if (top.visible) {
                MemeTextOverlay(
                    value = top.text,
                    onValueChange = { v -> editCaptions { it.copy(top = it.top.copy(text = v)) } },
                    placeholder = stringResource(R.string.default_top_text),
                    offset = top.toOffset(),
                    onOffsetChange = { o -> editCaptions { it.copy(top = it.top.withOffset(o)) } },
                    size = top.toSize(),
                    onSizeChange = { s -> editCaptions { it.copy(top = it.top.withSize(s)) } },
                    style = top.toStyle(),
                    capturing = capturing,
                    parentSize = canvasSize,
                    onDelete = {
                        val restore = top
                        onCaptionsChange { snap ->
                            snap.copy(top = snap.top.copy(visible = false, offsetX = 0f, offsetY = 0f, width = null, height = null))
                        }
                        onCaptionDeleted {
                            onCaptionsChange { snap -> snap.copy(top = restore) }
                        }
                    },
                    onOpenStyleSheet = {},
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            if (bottom.visible) {
                MemeTextOverlay(
                    value = bottom.text,
                    onValueChange = { v -> editCaptions { it.copy(bottom = it.bottom.copy(text = v)) } },
                    placeholder = stringResource(R.string.default_bottom_text),
                    offset = bottom.toOffset(),
                    onOffsetChange = { o -> editCaptions { it.copy(bottom = it.bottom.withOffset(o)) } },
                    size = bottom.toSize(),
                    onSizeChange = { s -> editCaptions { it.copy(bottom = it.bottom.withSize(s)) } },
                    style = bottom.toStyle(),
                    capturing = capturing,
                    parentSize = canvasSize,
                    onDelete = {
                        val restore = bottom
                        onCaptionsChange { snap ->
                            snap.copy(bottom = snap.bottom.copy(visible = false, offsetX = 0f, offsetY = 0f, width = null, height = null))
                        }
                        onCaptionDeleted {
                            onCaptionsChange { snap -> snap.copy(bottom = restore) }
                        }
                    },
                    onOpenStyleSheet = {},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Top-right pill for importing from gallery (independent of generation state)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Space900.copy(alpha = 0.75f))
                .clickable { onPickImage() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = Photon500,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.use_photo),
                style = MaterialTheme.typography.labelMedium,
                color = TextHigh
            )
        }
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
            delay(1000.milliseconds)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Red500,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "TRANSPORTER MALFUNCTION",
            style = MaterialTheme.typography.titleMedium,
            color = Red500,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (error) {
                is GenerationError.Unexpected -> error.detail
                is GenerationError.Server -> "Server error \${error.httpCode}"
                is GenerationError.RateLimited -> "Rate limited. Try again later."
                GenerationError.Timeout -> "Request timed out."
                GenerationError.AuthRejected -> "Authentication failed."
                GenerationError.OutOfCredit -> "Out of credits."
                GenerationError.ModelUnavailable -> "Model unavailable."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Space800)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Refresh, null, tint = TextHigh, modifier = Modifier.size(18.dp))
            Text("RETRY", color = TextHigh, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onPromptChip: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Space500,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AWAITING COORDINATES",
            style = MaterialTheme.typography.titleMedium,
            color = TextLow,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter a prompt below or pick a quick start.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PromptChip("a cute orange cat", onPromptChip)
            Spacer(modifier = Modifier.size(8.dp))
            PromptChip("cyberpunk city street", onPromptChip)
            Spacer(modifier = Modifier.size(8.dp))
            PromptChip("dog flying in space", onPromptChip)
        }
    }
}

@Composable
private fun PromptChip(text: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Space800)
            .clickable { onClick(text) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextHigh
        )
    }
}
