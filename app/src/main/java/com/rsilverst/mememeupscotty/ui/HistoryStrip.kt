package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rsilverst.mememeupscotty.ui.theme.Plasma500
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space900
import com.rsilverst.mememeupscotty.ui.theme.TextLow
import com.rsilverst.mememeupscotty.ui.theme.TextMid
import java.io.File

// Session-long horizontal carousel of every image loaded onto the canvas
// (generated + gallery-picked). Most-recent at index 0 (left edge). Hidden
// until the user has at least two entries — a strip-of-one is just visual
// noise. In-memory only; the on-disk files are swept on cold start.

@Composable
internal fun HistoryStrip(
    history: List<File>,
    selectedFile: File?,
    onSelect: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) return

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        ) {
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
                        onClick = { onSelect(file) }
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
}

@Composable
private fun HistoryThumb(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Plasma500 else Space500
    val borderWidth = if (isSelected) 2.dp else 1.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Space900,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.size(52.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
