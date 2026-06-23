package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.Plasma700
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space600
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space800
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptionStyleSheet(
    style: CaptionStyle,
    onStyleChange: (CaptionStyle) -> Unit,
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
            Text(
                text = stringResource(R.string.caption_style_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextHigh
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.caption_style_font))
            Spacer(Modifier.height(8.dp))
            FontRow(
                selected = style.font,
                onSelect = { onStyleChange(style.copy(font = it)) }
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.caption_style_color))
            Spacer(Modifier.height(8.dp))
            ColorRow(
                selected = style.fill,
                onSelect = { onStyleChange(style.copy(fill = it)) }
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.caption_style_align))
            Spacer(Modifier.height(8.dp))
            AlignRow(
                selected = style.alignment,
                onSelect = { onStyleChange(style.copy(alignment = it)) }
            )

            Spacer(Modifier.height(20.dp))
            OutlineRow(
                outline = style.outline,
                onChange = { onStyleChange(style.copy(outline = it)) }
            )

            Spacer(Modifier.height(12.dp))
            AllCapsRow(
                allCaps = style.allCaps,
                onChange = { onStyleChange(style.copy(allCaps = it)) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextLow
    )
}

@Composable
private fun FontRow(
    selected: CaptionFont,
    onSelect: (CaptionFont) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CaptionFont.entries.forEach { font ->
            FontSwatch(
                font = font,
                isSelected = font == selected,
                onClick = { onSelect(font) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FontSwatch(
    font: CaptionFont,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(
        when (font) {
            CaptionFont.IMPACT    -> R.string.caption_font_impact
            CaptionFont.BOLD_SANS -> R.string.caption_font_sans
            CaptionFont.SERIF     -> R.string.caption_font_serif
        }
    )
    val borderColor = if (isSelected) Plasma700 else Space500
    val bg = if (isSelected) Space600.copy(alpha = 0.6f) else Space800
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = font.fontFamily,
                    fontWeight = font.fontWeight,
                    fontSize = 18.sp
                ),
                color = if (isSelected) TextHigh else TextMid
            )
        }
    }
}

@Composable
private fun ColorRow(
    selected: CaptionFill,
    onSelect: (CaptionFill) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CaptionFill.entries.forEach { fill ->
            ColorSwatch(
                fill = fill,
                isSelected = fill == selected,
                onClick = { onSelect(fill) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    fill: CaptionFill,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(
        when (fill) {
            CaptionFill.WHITE  -> R.string.caption_color_white
            CaptionFill.YELLOW -> R.string.caption_color_yellow
            CaptionFill.BLACK  -> R.string.caption_color_black
            CaptionFill.RED    -> R.string.caption_color_red
        }
    )
    val ringColor = if (isSelected) Plasma700 else Space500
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, ringColor),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(fill.color)
                    .border(1.dp, Space500.copy(alpha = 0.6f), CircleShape)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = label,
                    tint = if (fill == CaptionFill.WHITE || fill == CaptionFill.YELLOW) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AlignRow(
    selected: CaptionAlign,
    onSelect: (CaptionAlign) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AlignButton(
            icon = Icons.AutoMirrored.Outlined.FormatAlignLeft,
            label = stringResource(R.string.caption_align_left),
            isSelected = selected == CaptionAlign.LEFT,
            onClick = { onSelect(CaptionAlign.LEFT) },
            modifier = Modifier.weight(1f)
        )
        AlignButton(
            icon = Icons.Outlined.FormatAlignCenter,
            label = stringResource(R.string.caption_align_center),
            isSelected = selected == CaptionAlign.CENTER,
            onClick = { onSelect(CaptionAlign.CENTER) },
            modifier = Modifier.weight(1f)
        )
        AlignButton(
            icon = Icons.AutoMirrored.Outlined.FormatAlignRight,
            label = stringResource(R.string.caption_align_right),
            isSelected = selected == CaptionAlign.RIGHT,
            onClick = { onSelect(CaptionAlign.RIGHT) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlignButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Plasma700 else Space500
    val bg = if (isSelected) Space600.copy(alpha = 0.6f) else Space800
    val tint = if (isSelected) Plasma700 else TextMid
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        }
    }
}

@Composable
private fun OutlineRow(
    outline: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Space800)
            .border(1.dp, Space500, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.caption_style_outline),
            style = MaterialTheme.typography.bodyLarge,
            color = TextHigh,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = outline,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Space900,
                checkedTrackColor = Plasma700,
                uncheckedThumbColor = TextLow,
                uncheckedTrackColor = Space600,
                uncheckedBorderColor = Space500
            )
        )
    }
}

@Composable
private fun AllCapsRow(
    allCaps: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Space800)
            .border(1.dp, Space500, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.caption_style_all_caps),
            style = MaterialTheme.typography.bodyLarge,
            color = TextHigh,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = allCaps,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Space900,
                checkedTrackColor = Plasma700,
                uncheckedThumbColor = TextLow,
                uncheckedTrackColor = Space600,
                uncheckedBorderColor = Space500
            )
        )
    }
}
