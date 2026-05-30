package com.rsilverst.mememeupscotty.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.MemeCaptionFontFamily
import com.rsilverst.mememeupscotty.ui.theme.Plasma300
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Plasma700
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space600
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space900
import kotlin.math.roundToInt

// Draggable, resizable, focusable meme caption. The selection chrome
// (dashed border, delete chip, resize handle) only appears on focus and
// is suppressed during bitmap capture so it doesn't end up in the saved
// meme.

@Composable
internal fun MemeTextOverlay(
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
        border = BorderStroke(1.dp, Space500),
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
internal fun AddTextPill(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Space700.copy(alpha = 0.85f),
        contentColor = Plasma500,
        border = BorderStroke(1.dp, Plasma700),
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

// Caption font fitting — binary-stepped largest-fit search across the
// allowed width × height envelope. Cached by callers with remember(...)
// so it doesn't run on every recomposition.
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
