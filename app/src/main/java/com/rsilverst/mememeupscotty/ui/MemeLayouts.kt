package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.viewmodel.CaptionSnapshot
import com.rsilverst.mememeupscotty.ui.viewmodel.GenerationState
import com.rsilverst.mememeupscotty.ui.viewmodel.HistoryEntry
import com.rsilverst.mememeupscotty.ui.viewmodel.ImageModel

// Phone layout: the visual focal point (the Canvas) is placed
// prominently at the top, followed by the history strip and
// input controls underneath. Save/Share sits at the bottom
// carrying the navigationBars inset for edge-to-edge clearance.

@Composable
internal fun CompactLayout(
    modifier: Modifier,
    prompt: String,
    onPromptChange: (String) -> Unit,
    captions: CaptionSnapshot,
    onCaptionsChange: ((CaptionSnapshot) -> CaptionSnapshot) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit,
    onPickImage: () -> Unit,
    generationHistory: List<HistoryEntry>,
    activeEntry: HistoryEntry?,
    onSelectFromHistory: (java.io.File) -> Unit,
    onDeleteFromHistory: (java.io.File) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val activeFile = activeEntry?.file
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            // Tap outside any text field to dismiss focus + keyboard. Taps on
            // BasicTextFields are consumed by their own focus handlers and do
            // not bubble up, so this only fires for "empty space" taps.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            // Shrink the scroll viewport by the IME height when the keyboard
            // opens. Combined with BasicTextField's default bring-into-view
            // on focus, this lets tapping a caption (especially the bottom
            // one) auto-scroll the caption above the keyboard instead of
            // leaving it occluded.
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        HudStrip(
            selectedModel = selectedModel,
            onOpenModelPicker = onOpenModelPicker
        )

        Spacer(modifier = Modifier.height(16.dp))

        MemeCanvas(
            generationState = generationState,
            activeEntry = activeEntry,
            captions = captions,
            onCaptionsChange = onCaptionsChange,
            capturing = capturing,
            graphicsLayer = graphicsLayer,
            onPromptChip = onPromptChip,
            onRetry = onEnergize,
            onCaptionDeleted = onCaptionDeleted,
            onPickImage = onPickImage
        )

        if (generationHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HistoryStrip(
                history = generationHistory,
                selectedFile = activeFile,
                onSelect = onSelectFromHistory,
                onDelete = onDeleteFromHistory,
                onClearAll = onClearAllHistory
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        PromptInput(
            value = prompt,
            onValueChange = onPromptChange,
            singleLine = false,
            minLines = 3,
            onSubmit = onEnergize
        )

        Spacer(modifier = Modifier.height(16.dp))

        EnergizeButton(
            generationState = generationState,
            hasGeneratedImage = hasGeneratedImage,
            onClick = onEnergize,
            onCancel = onCancel
        )

        Spacer(modifier = Modifier.height(20.dp))

        val isLoading = generationState is GenerationState.Loading
        val hasImage = activeFile != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
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
    captions: CaptionSnapshot,
    onCaptionsChange: ((CaptionSnapshot) -> CaptionSnapshot) -> Unit,
    generationState: GenerationState,
    selectedModel: ImageModel,
    hasGeneratedImage: Boolean,
    capturing: Boolean,
    graphicsLayer: GraphicsLayer,
    onOpenModelPicker: () -> Unit,
    onEnergize: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onPromptChip: (String) -> Unit,
    onCaptionDeleted: (onUndo: () -> Unit) -> Unit,
    onPickImage: () -> Unit,
    generationHistory: List<HistoryEntry>,
    activeEntry: HistoryEntry?,
    onSelectFromHistory: (java.io.File) -> Unit,
    onDeleteFromHistory: (java.io.File) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val activeFile = activeEntry?.file
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
        ) {
            MemeCanvas(
                generationState = generationState,
                activeEntry = activeEntry,
                captions = captions,
                onCaptionsChange = onCaptionsChange,
                capturing = capturing,
                graphicsLayer = graphicsLayer,
                onPromptChip = onPromptChip,
                onRetry = onEnergize,
                onCaptionDeleted = onCaptionDeleted,
                onPickImage = onPickImage,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.height(20.dp))
            HudStrip(
                selectedModel = selectedModel,
                onOpenModelPicker = onOpenModelPicker
            )
            if (generationHistory.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HistoryStrip(
                    history = generationHistory,
                    selectedFile = activeFile,
                    onSelect = onSelectFromHistory,
                    onDelete = onDeleteFromHistory,
                    onClearAll = onClearAllHistory
                )
            }
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
                minLines = 3,
                onSubmit = onEnergize
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
                onClick = onEnergize,
                onCancel = onCancel
            )

            val isLoading = generationState is GenerationState.Loading
            val hasImage = activeFile != null
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
