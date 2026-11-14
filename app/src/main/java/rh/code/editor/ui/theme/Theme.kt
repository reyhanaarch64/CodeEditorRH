package rh.code.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0D0D0D)
val Surface = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF252525)
val Primary = Color(0xFF0A84FF)
val OnPrimary = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFE5E5E5)
val TextSecondary = Color(0xFF8E8E93)
val TabActive = Color(0xFF0A84FF)
val TabInactive = Color(0xFF3A3A3C)
val Border = Color(0xFF2C2C2E)
val Error = Color(0xFFFF453A)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Error
)

@Composable
fun CodeEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
