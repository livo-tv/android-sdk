package tv.livo.sdk.studio

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public data class StudioTheme(
    val primary: Color = Color(0xFF226BC0),
    val live: Color = Color(0xFFD6363B),
    val destructive: Color = Color(0xFFD6363B),
    val radius: Dp = 14.dp,
    val dark: Boolean = false,
)

public val LocalStudioTheme = staticCompositionLocalOf { StudioTheme() }
