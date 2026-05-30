package com.rsilverst.mememeupscotty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rsilverst.mememeupscotty.ui.theme.MemeMeUpScottyTheme
import com.rsilverst.mememeupscotty.ui.theme.Space500
import com.rsilverst.mememeupscotty.ui.theme.Space700
import com.rsilverst.mememeupscotty.ui.theme.Space900

// Shared dark Space900 background used by every @Preview in this package
// so previews match the app's runtime appearance.
internal const val PREVIEW_BG = 0xFF0B0E1FL

@Composable
internal fun PreviewShell(
    width: Int = 360,
    content: @Composable () -> Unit
) {
    MemeMeUpScottyTheme {
        Box(
            modifier = Modifier
                .background(Space900)
                .padding(16.dp)
                .width(width.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun PreviewCanvasFrame(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(328.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Space700)
            .border(1.dp, Space500, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
        content = content
    )
}
