package rh.code.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0D0D0F)
val Surface = Color(0xFF161618)
val SurfaceVariant = Color(0xFF1E1E22)
val SurfaceElevated = Color(0xFF25252A)
val Primary = Color(0xFF0A84FF)
val PrimaryVariant = Color(0xFF409CFF)
val OnPrimary = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFE8E8ED)
val TextSecondary = Color(0xFF8E8E93)
val TextTertiary = Color(0xFF636366)
val TabActive = Color(0xFF0A84FF)
val TabInactive = Color(0xFF2C2C2E)
val Border = Color(0xFF2C2C2E)
val Error = Color(0xFFFF453A)
val Success = Color(0xFF30D158)
val LineNumber = Color(0xFF48484A)
val Cursor = Color(0xFF0A84FF)

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
