package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Space400
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space600
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel
import java.io.File

// HUD strip above the canvas: shows the active model + a re-roll button.
// Used by both compact and expanded layouts.

@Composable
internal fun HudStrip(
    selectedModel: ImageModel,
    generationState: GenerationState,
    onOpenModelPicker: () -> Unit,
    onReroll: () -> Unit
) {
    val isLoading = generationState is GenerationState.Loading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Space600.copy(alpha = 0.6f))
            .border(1.dp, Space500, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenModelPicker() }) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Plasma500.copy(alpha = 0.12f))
                    .border(1.dp, Plasma500.copy(alpha = 0.30f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedModel.shortGlyph,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    color = Plasma500
                )
            }
            Text(
                text = selectedModel.shortLabel,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.2.sp),
                color = TextHigh
            )
            Text(
                text = "·",
                color = Space400,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = stringResource(R.string.hud_tap_to_change),
                style = MaterialTheme.typography.labelMedium,
                color = TextLow
            )
        }

        HudIconButton(
            icon = Icons.Filled.Casino,
            contentDescription = stringResource(R.string.hud_reroll),
            enabled = !isLoading,
            onClick = onReroll
        )
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.04f),
        contentColor = if (enabled) TextMid else TextLow,
        border = BorderStroke(1.dp, Space500),
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============================================================================
// Dock — bottom prompt + Energize + Save/Share row (compact layout only).
// Expanded layout assembles these same controls in its own column.
// ============================================================================

@Composable
internal fun Dock(
    prompt: String,
    onPromptChange: (String) -> Unit,
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        // Lift the Save/Share row above the 3-button nav bar in edge-to-edge
        // mode. Scaffold without a bottomBar does not always emit a bottom
        // inset in its content paddingValues, so we apply it explicitly.
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        PromptInput(
            value = prompt,
            onValueChange = onPromptChange,
            singleLine = false,
            minLines = 3,
            onSubmit = onEnergize
        )

        EnergizeButton(
            generationState = generationState,
            hasGeneratedImage = hasGeneratedImage,
            onClick = onEnergize
        )

        val isLoading = generationState is GenerationState.Loading
        val hasImage = generationState is GenerationState.Success
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GhostButton(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.save),
                enabled = !isLoading && hasImage,
                onClick = onSave,
                modifier = Modifier.weight(1f)
            )
            GhostButton(
                icon = Icons.Filled.IosShare,
                label = stringResource(R.string.share),
                enabled = !isLoading && hasImage,
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    minLines: Int = 1,
    onSubmit: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) Plasma500 else Space500
    // BasicTextField rejects minLines != 1 when singleLine is true. Guard so
    // a misconfigured call doesn't crash at runtime.
    val effectiveMinLines = if (singleLine) 1 else minLines

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Space700)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = TextLow,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 1.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = effectiveMinLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextHigh),
            cursorBrush = SolidColor(Plasma500),
            // Show "Go" as the keyboard's action key so the user can fire
            // Energize without dismissing the keyboard first. Multi-line
            // mode keeps the separate Enter key for newlines; Go is the
            // distinct IME action.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { onSubmit?.invoke() }
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow
                    )
                }
                inner()
            }
        )
    }
}

@Composable
internal fun EnergizeButton(
    generationState: GenerationState,
    hasGeneratedImage: Boolean,
    onClick: () -> Unit
) {
    val isLoading = generationState is GenerationState.Loading
    val label = when {
        isLoading -> stringResource(R.string.generating)
        hasGeneratedImage || generationState is GenerationState.Success ->
            stringResource(R.string.regenerate)
        else -> stringResource(R.string.generate)
    }
    val containerColor = if (isLoading) Space600 else Plasma500
    val contentColor = if (isLoading) Plasma500 else Space900

    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        if (!isLoading) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = 2.5.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
internal fun GhostButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (enabled) TextMid else TextMid.copy(alpha = 0.4f)
    val borderColor = if (enabled) Space500 else Space500.copy(alpha = 0.5f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
            )
        }
    }
}

// ============================================================================
// Previews
// ============================================================================

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 80)
@Composable
private fun HudStripPreview() {
    PreviewShell {
        HudStrip(
            selectedModel = ImageModel.JUGGERNAUT,
            generationState = GenerationState.Success(File("")),
            onOpenModelPicker = {},
            onReroll = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Generate() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Idle,
            hasGeneratedImage = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Regenerate() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Success(File("")),
            hasGeneratedImage = true,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun EnergizeButtonPreview_Loading() {
    PreviewShell {
        EnergizeButton(
            generationState = GenerationState.Loading,
            hasGeneratedImage = false,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun PromptInputPreview_Empty() {
    PreviewShell {
        PromptInput(value = "", onValueChange = {}, singleLine = true)
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 100)
@Composable
private fun PromptInputPreview_Filled() {
    PreviewShell {
        PromptInput(
            value = "a corgi as the captain of the enterprise",
            onValueChange = {},
            singleLine = false
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 280)
@Composable
private fun DockPreview_NoImage() {
    PreviewShell {
        Dock(
            prompt = "",
            onPromptChange = {},
            generationState = GenerationState.Idle,
            hasGeneratedImage = false,
            onEnergize = {},
            onSave = {},
            onShare = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = PREVIEW_BG, widthDp = 360, heightDp = 280)
@Composable
private fun DockPreview_WithImage() {
    PreviewShell {
        Dock(
            prompt = "a corgi as the captain",
            onPromptChange = {},
            generationState = GenerationState.Success(File("")),
            hasGeneratedImage = true,
            onEnergize = {},
            onSave = {},
            onShare = {}
        )
    }
}
