package com.rsilverst.mememeupscotty.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rsilverst.mememeupscotty.ui.theme.BodyFontFamily
import com.rsilverst.mememeupscotty.ui.theme.MemeCaptionFontFamily

// Defaults (IMPACT + WHITE + CENTER + outline) match the original
// hardcoded rendering so existing memes look unchanged.

internal enum class CaptionFont(
    val fontFamily: FontFamily,
    val fontWeight: FontWeight
) {
    IMPACT(MemeCaptionFontFamily, FontWeight.Black),
    BOLD_SANS(BodyFontFamily, FontWeight.Bold),
    SERIF(FontFamily.Serif, FontWeight.Bold)
}

internal enum class CaptionFill(val color: Color) {
    WHITE(Color.White),
    YELLOW(Color(0xFFFFD53D)),
    BLACK(Color.Black),
    RED(Color(0xFFE74C4C))
}

internal enum class CaptionAlign(val textAlign: TextAlign) {
    LEFT(TextAlign.Start),
    CENTER(TextAlign.Center),
    RIGHT(TextAlign.End)
}

internal data class CaptionStyle(
    val font: CaptionFont = CaptionFont.IMPACT,
    val fill: CaptionFill = CaptionFill.WHITE,
    val alignment: CaptionAlign = CaptionAlign.CENTER,
    val outline: Boolean = true
)
