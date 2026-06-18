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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.FormatColorText
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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
    style: CaptionStyle,
    capturing: Boolean,
    parentSize: IntSize,
    onDelete: () -> Unit,
    onOpenStyleSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fontFamily = style.font.fontFamily
    val fontWeight = style.font.fontWeight
    val fillColor = style.fill.color
    val textAlign = style.alignment.textAlign
    val textMeasurer = rememberTextMeasurer()
    val minWidthPx = with(density) { 60.dp.toPx() }
    val minHeightPx = with(density) { 40.dp.toPx() }
    var currentRenderedSize by remember { mutableStateOf(IntSize.Zero) }
    var positionInParent by remember { mutableStateOf(Offset.Zero) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val latestOffset by rememberUpdatedState(offset)
    val latestOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val latestSize by rememberUpdatedState(size)
    val latestOnSizeChange by rememberUpdatedState(onSizeChange)
    val latestPositionInParent by rememberUpdatedState(positionInParent)
    val latestRenderedSize by rememberUpdatedState(currentRenderedSize)
    val latestParentSize by rememberUpdatedState(parentSize)

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
            .onGloballyPositioned { coords ->
                positionInParent = coords.boundsInParent().topLeft
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Clamp the drag so the overlay rect stays inside the
                    // canvas. positionInParent already reflects the current
                    // offset; we derive the anchor (where the overlay sits
                    // when offset = Zero) so we can clamp the desired absolute
                    // position rather than the offset delta directly.
                    val w = latestRenderedSize.width.toFloat()
                    val h = latestRenderedSize.height.toFloat()
                    val maxX = (latestParentSize.width - w).coerceAtLeast(0f)
                    val maxY = (latestParentSize.height - h).coerceAtLeast(0f)
                    val anchorX = latestPositionInParent.x - latestOffset.x
                    val anchorY = latestPositionInParent.y - latestOffset.y
                    val desiredX = latestOffset.x + dragAmount.x
                    val desiredY = latestOffset.y + dragAmount.y
                    val clampedX = (anchorX + desiredX).coerceIn(0f, maxX) - anchorX
                    val clampedY = (anchorY + desiredY).coerceIn(0f, maxY) - anchorY
                    latestOnOffsetChange(Offset(clampedX, clampedY))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val innerModifier = if (size != null) {
            Modifier.fillMaxSize().padding(CAPTION_INNER_PADDING)
        } else {
            Modifier.padding(CAPTION_INNER_PADDING)
        }

        // Visual padded inner box representing the dashed text bounds
        Box(
            modifier = innerModifier
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
            val maxHeightPx = if (size != null) {
                (size.height - with(density) { (CAPTION_INNER_PADDING * 2).toPx() }).toInt().coerceAtLeast(0)
            } else {
                with(density) { CAPTION_DEFAULT_MAX_HEIGHT.toPx() }.toInt()
            }
            val displayedValue = if (style.allCaps) value.uppercase() else value
            val displayText = displayedValue.ifEmpty { placeholder }
            val fontSize = remember(displayText, maxWidthPx, maxHeightPx, fontFamily, fontWeight) {
                findBestFitFontSize(displayText, textMeasurer, fontFamily, fontWeight, maxWidthPx, maxHeightPx)
            }
            val strokeWidthPx = with(density) { (fontSize.value * CAPTION_STROKE_RATIO).sp.toPx() }
            val baseStyle = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = baseStyle.copy(color = fillColor),
                cursorBrush = SolidColor(Plasma500),
                visualTransformation = if (style.allCaps) UppercaseVisualTransformation else VisualTransformation.None,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { hasFocus = it.isFocused },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            displayedValue.isNotEmpty() && style.outline -> {
                                Text(
                                    text = displayedValue,
                                    style = baseStyle.copy(
                                        color = Color.Black,
                                        drawStyle = Stroke(
                                            width = strokeWidthPx,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                )
                            }
                            displayedValue.isEmpty() && !capturing -> {
                                Text(
                                    text = placeholder,
                                    style = baseStyle.copy(
                                        color = fillColor.copy(alpha = if (hasFocus) 0.55f else 0.30f)
                                    )
                                )
                            }
                        }
                        innerTextField()
                    }
                }
            )
        } // End of BoxWithConstraints
        } // End of inner Box

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
                    val parentW = latestParentSize.width.toFloat().coerceAtLeast(minWidthPx)
                    val parentH = latestParentSize.height.toFloat().coerceAtLeast(minHeightPx)
                    val newW = (startW + dragAmount.x).coerceIn(minWidthPx, parentW)
                    val newH = (startH + dragAmount.y).coerceIn(minHeightPx, parentH)
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
            DeleteChip(
                onClick = onDelete,
                modifier = Modifier.offset(x = (-2).dp, y = 2.dp)
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            StyleChip(
                onClick = onOpenStyleSheet,
                modifier = Modifier.offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun ResizeHandle(onDrag: (Offset) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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
private fun DeleteChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Space600,
        contentColor = Red500,
        border = BorderStroke(1.dp, Space500),
        modifier = modifier
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
private fun StyleChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Space600,
        contentColor = Plasma500,
        border = BorderStroke(1.dp, Space500),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FormatColorText,
                contentDescription = stringResource(R.string.edit_caption_style),
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

// Layout constants shared by the interactive overlay and the read-only
// StaticMemeCaption so a caption renders identically on the canvas and (scaled)
// in a history thumbnail. Drift here would break that WYSIWYG guarantee.
internal val CAPTION_INNER_PADDING = 18.dp
internal val CAPTION_DEFAULT_MAX_HEIGHT = 96.dp
internal const val CAPTION_STROKE_RATIO = 0.15f

// Caption font fitting — binary-stepped largest-fit search across the
// allowed width × height envelope. Cached by callers with remember(...)
// so it doesn't run on every recomposition.
internal fun findBestFitFontSize(
    text: String,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    maxWidthPx: Int,
    maxHeightPx: Int
): TextUnit {
    if (text.isEmpty() || maxWidthPx <= 0 || maxHeightPx <= 0) return 40.sp
    val widthConstraints = Constraints(maxWidth = maxWidthPx)
    
    val fontSizes = (14..40 step 2).toList()
    
    var low = 0
    var high = fontSizes.lastIndex
    var bestFitIndex = 0 // Defaults to minimum size (14.sp)
    
    while (low <= high) {
        val mid = (low + high) ushr 1
        val size = fontSizes[mid]
        
        val layout = textMeasurer.measure(
            text = text,
            style = TextStyle(
                fontSize = size.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center
            ),
            constraints = widthConstraints
        )
        
        if (layout.size.height <= maxHeightPx) {
            bestFitIndex = mid
            low = mid + 1 // Fits! Try to find a larger fitting size
        } else {
            high = mid - 1 // Too tall. Look for a smaller size
        }
    }
    
    return fontSizes[bestFitIndex].sp
}

internal object UppercaseVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Use uppercaseChar() to guarantee a strict 1:1 length mapping for OffsetMapping.Identity
        val uppercaseText = text.text.map { it.uppercaseChar() }.joinToString("")
        return TransformedText(
            text = AnnotatedString(uppercaseText),
            offsetMapping = OffsetMapping.Identity
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
