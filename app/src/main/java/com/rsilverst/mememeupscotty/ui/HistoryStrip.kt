package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionData
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionSnapshot
import com.rsilverst.mememeupscotty.ui.theme.Photon500
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import com.rsilverst.mememeupscotty.ui.viewmodel.HistoryEntry
import java.io.File

// Horizontal carousel of every image loaded onto the canvas
// (generated + gallery-picked). Most-recent at index 0 (left edge). Hidden
// until the user has at least one entry. Persisted on disk and indexed via DataStore.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryStrip(
    history: List<HistoryEntry>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    onDelete: (File) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "STARDATE ${getCurrentStardate()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· ${history.size} entries",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMid
                )
            }

            TextButton(
                onClick = { showClearAllDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_clear_action).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Photon500
                )
            }
        }

        // Stack: LazyRow underneath, fade gradient on top of the right edge
        // to advertise "more available, scroll for it" even before the user
        // has touched the strip.
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(history, key = { it.file.absolutePath }) { entry ->
                    HistoryThumb(
                        file = entry.file,
                        captions = entry.captions,
                        isSelected = entry.file == selectedFile,
                        onClick = { onSelect(entry.file) },
                        onLongClick = { fileToDelete = entry.file }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(32.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Space900)
                        )
                    )
            )
        }
    }

    // Material 3 Dialogs for Delete / Clear confirmations
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete?.let { onDelete(it) }
                        fileToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.history_delete_confirm), color = Red500)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.history_delete_cancel), color = TextLow)
                }
            },
            containerColor = Space700,
            titleContentColor = TextHigh,
            textContentColor = TextMid
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.history_clear_confirm), color = Red500)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.history_delete_cancel), color = TextLow)
                }
            },
            containerColor = Space700,
            titleContentColor = TextHigh,
            textContentColor = TextMid
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryThumb(
    file: File,
    captions: CaptionSnapshot,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = if (isSelected) Plasma500 else Space500
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Space900,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = 6.dp,
        modifier = Modifier.size(64.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(9.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.history_thumb_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // The captions aren't baked into the file (they stay editable), so
            // paint a scaled, read-only preview on top so each entry reads as
            // the meme it is, not just its source image.
            ThumbnailCaptions(captions = captions, modifier = Modifier.fillMaxSize())
        }
    }
}

// Read-only WYSIWYG render of the captions over a thumbnail. Wrapping matches
// the canvas because both the font size and the text-box width are scaled from
// the canvas reference by the same factor (thumb / refSize): the font is the
// size the canvas's auto-fit would pick at full width, multiplied by the scale,
// and the box width is the reference width times the scale — so the same number
// of characters fit per line. Entries with no refSize render nothing.
@Composable
private fun ThumbnailCaptions(captions: CaptionSnapshot, modifier: Modifier) {
    val refSize = captions.refSize?.takeIf { it > 0f } ?: return
    BoxWithConstraints(modifier = modifier) {
        val thumbPx = constraints.maxWidth.toFloat()
        if (thumbPx <= 0f) return@BoxWithConstraints
        val scale = thumbPx / refSize
        ThumbCaptionSlot(captions.top, refSize, scale, isTop = true)
        ThumbCaptionSlot(captions.bottom, refSize, scale, isTop = false)
    }
}

@Composable
private fun BoxScope.ThumbCaptionSlot(
    caption: CaptionData,
    refSize: Float,
    scale: Float,
    isTop: Boolean
) {
    if (!caption.visible || caption.text.isBlank()) return
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = caption.toStyle()
    val displayed = if (caption.allCaps) caption.text.uppercase() else caption.text

    // Canvas reference envelope (full px), mirroring MemeTextOverlay: the text
    // wraps within refSize minus the 16dp side insets and the inner padding.
    val insetPx = with(density) { 16.dp.toPx() }
    val innerPx = with(density) { CAPTION_INNER_PADDING.toPx() }
    val refWidthPx = (refSize - insetPx * 2 - innerPx * 2).toInt().coerceAtLeast(1)
    val refHeightPx = with(density) { CAPTION_DEFAULT_MAX_HEIGHT.toPx() }.toInt()
    val canvasFont = remember(displayed, refWidthPx, refHeightPx, style.font) {
        findBestFitFontSize(
            displayed, textMeasurer, style.font.fontFamily, style.font.fontWeight,
            refWidthPx, refHeightPx
        )
    }

    val fontSize = (canvasFont.value * scale).sp
    val strokePx = with(density) { (fontSize.value * CAPTION_STROKE_RATIO).sp.toPx() }
    // Text starts 16dp (inset) + inner padding below the canvas edge; scale that.
    val edgeInsetDp = with(density) { ((insetPx + innerPx) * scale).toDp() }
    val widthDp = with(density) { (refWidthPx * scale).toDp() }
    val offX = with(density) { (caption.offsetX * scale).toDp() }
    val offY = with(density) { (caption.offsetY * scale).toDp() }

    val baseStyle = TextStyle(
        fontSize = fontSize,
        fontFamily = style.font.fontFamily,
        fontWeight = style.font.fontWeight,
        textAlign = style.alignment.textAlign
    )

    Box(
        modifier = Modifier
            .align(if (isTop) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(top = if (isTop) edgeInsetDp else 0.dp, bottom = if (isTop) 0.dp else edgeInsetDp)
            .offset(x = offX, y = offY)
            .width(widthDp)
    ) {
        if (style.outline) {
            Text(
                text = displayed,
                style = baseStyle.copy(
                    color = Color.Black,
                    drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = displayed,
            style = baseStyle.copy(color = style.fill.color),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun getCurrentStardate(): String {
    val timestamp = System.currentTimeMillis()
    val stardate = (timestamp / 86400000.0 * 1000.0) % 100000.0
    return String.format(java.util.Locale.US, "%.1f", stardate)
}
