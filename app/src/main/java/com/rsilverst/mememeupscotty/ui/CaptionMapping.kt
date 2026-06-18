package com.rsilverst.mememeupscotty.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionData

// Bridges the persistence-friendly CaptionData (primitives + enum names, owned
// by the viewmodel layer) and the Compose types the overlay renders with. Bad
// enum names fall back to the defaults rather than crashing, so a hand-edited
// or future-version persisted snapshot can't take the canvas down.

internal fun CaptionData.toOffset(): Offset = Offset(offsetX, offsetY)

internal fun CaptionData.toSize(): Size? =
    if (width != null && height != null) Size(width, height) else null

internal fun CaptionData.toStyle(): CaptionStyle = CaptionStyle(
    font = enumOrDefault(font, CaptionFont.IMPACT),
    fill = enumOrDefault(fill, CaptionFill.WHITE),
    alignment = enumOrDefault(align, CaptionAlign.CENTER),
    outline = outline,
    allCaps = allCaps
)

internal fun CaptionData.withOffset(offset: Offset): CaptionData =
    copy(offsetX = offset.x, offsetY = offset.y)

internal fun CaptionData.withSize(size: Size): CaptionData =
    copy(width = size.width, height = size.height)

internal fun CaptionData.withStyle(style: CaptionStyle): CaptionData = copy(
    font = style.font.name,
    fill = style.fill.name,
    align = style.alignment.name,
    outline = style.outline,
    allCaps = style.allCaps
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
