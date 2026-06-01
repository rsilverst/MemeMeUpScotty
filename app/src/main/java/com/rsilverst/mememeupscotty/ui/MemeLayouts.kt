package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel

// Phone layout: canvas + HUD + Dock stacked vertically with scroll.

@Composable
internal fun CompactLayout(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        MemeCanvas(
            generationState = generationState,
            topText = topText,
            onTopTextChange = onTopTextChange,
            bottomText = bottomText,
            onBottomTextChange = onBottomTextChange,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onPromptChip = onPromptChip,
            onRetry = onEnergize,
            onCaptionDeleted = onCaptionDeleted
        )

        Spacer(modifier = Modifier.height(20.dp))

        HudStrip(
            selectedModel = selectedModel,
            generationState = generationState,
            onOpenModelPicker = onOpenModelPicker,
            onReroll = onEnergize
        )

        Spacer(modifier = Modifier.height(24.dp))

        Dock(
            prompt = prompt,
            onPromptChange = onPromptChange,
            generationState = generationState,
            hasGeneratedImage = hasGeneratedImage,
            onEnergize = onEnergize,
            onSave = onSave,
            onShare = onShare
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Tablet layout: canvas + HUD on the left, prompt + Engine + Energize +
// Save/Share stacked on the right.

@Composable
internal fun ExpandedLayout(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    topText: String,
    onTopTextChange: (String) -> Unit,
    bottomText: String,
    onBottomTextChange: (String) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
        ) {
            MemeCanvas(
                generationState = generationState,
                topText = topText,
                onTopTextChange = onTopTextChange,
                bottomText = bottomText,
                onBottomTextChange = onBottomTextChange,
                capturing = capturing,
                graphicsLayer = graphicsLayer,
                onPromptChip = onPromptChip,
                onRetry = onEnergize,
                onCaptionDeleted = onCaptionDeleted,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(20.dp))
            HudStrip(
                selectedModel = selectedModel,
                generationState = generationState,
                onOpenModelPicker = onOpenModelPicker,
                onReroll = onEnergize
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FieldLabel(text = stringResource(R.string.field_label_prompt))
            PromptInput(
                value = prompt,
                onValueChange = onPromptChange,
                singleLine = false,
                minLines = 3
            )

            FieldLabel(text = stringResource(R.string.field_label_engine))
            ModelSelectorButton(
                model = selectedModel,
                onClick = onOpenModelPicker
            )

            Spacer(Modifier.height(4.dp))

            EnergizeButton(
                generationState = generationState,
                hasGeneratedImage = hasGeneratedImage,
                onClick = onEnergize
            )

            val isLoading = generationState is GenerationState.Loading
            val hasImage = generationState is GenerationState.Success
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextLow,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
