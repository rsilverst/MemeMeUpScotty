package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rsilverst.mememeupscotty.R
import com.rsilverst.mememeupscotty.ui.theme.Photon500
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Red500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextHigh
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import java.io.File

// Horizontal carousel of every image loaded onto the canvas
// (generated + gallery-picked). Most-recent at index 0 (left edge). Hidden
// until the user has at least one entry. Persisted on disk and indexed via DataStore.

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryStrip(
    history: List<File>,
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
                    text = stringResource(R.string.history_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· ${history.size}",
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
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(history, key = { it.absolutePath }) { file ->
                    HistoryThumb(
                        file = file,
                        isSelected = file == selectedFile,
                        onClick = { onSelect(file) },
                        onLongClick = { fileToDelete = file }
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
        modifier = Modifier.size(52.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.history_thumb_content_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
            )
        }
    }
}
