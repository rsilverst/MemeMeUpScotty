package com.rsilverst.mememeupscotty.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StardateColorScheme = darkColorScheme(
    primary           = Plasma500,
    onPrimary         = Space900,
    primaryContainer  = Plasma700,
    onPrimaryContainer = Space900,

    secondary         = Photon500,
    onSecondary       = Space900,

    tertiary          = Solar500,
    onTertiary        = Space900,

    background        = Space900,
    onBackground      = TextHigh,

    surface           = Space700,
    onSurface         = TextHigh,
    surfaceVariant    = Space600,
    onSurfaceVariant  = TextMid,

    surfaceContainerLowest = Space800,
    surfaceContainerLow    = Space700,
    surfaceContainer       = Space600,
    surfaceContainerHigh   = Space500,
    surfaceContainerHighest = Space400,

    outline           = Space500,
    outlineVariant    = Space600,

    error             = Red500,
    onError           = Space900,

    inverseSurface    = Space500,
    inverseOnSurface  = TextHigh,
    inversePrimary    = Plasma700
)

@Composable
fun MemeMeUpScottyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StardateColorScheme,
        typography = Typography,
        content = content
    )
}
