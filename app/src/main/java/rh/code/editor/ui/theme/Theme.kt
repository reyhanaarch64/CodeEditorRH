package rh.code.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { true }

// dark
val DarkBackground = Color(0xFF0D0D0F)
val DarkSurface = Color(0xFF161618)
val DarkSurfaceVariant = Color(0xFF1E1E22)
val DarkSurfaceElevated = Color(0xFF25252A)
val DarkTextPrimary = Color(0xFFE8E8ED)
val DarkTextSecondary = Color(0xFF8E8E93)
val DarkLineNumber = Color(0xFF48484A)

// light
val LightBackground = Color(0xFFF2F2F7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE5E5EA)
val LightSurfaceElevated = Color(0xFFD1D1D6)
val LightTextPrimary = Color(0xFF1C1C1E)
val LightTextSecondary = Color(0xFF636366)
val LightLineNumber = Color(0xFFAEAEB2)

val Primary = Color(0xFF0A84FF)
val OnPrimary = Color(0xFFFFFFFF)
val Error = Color(0xFFFF453A)
val Success = Color(0xFF30D158)
val BorderDark = Color(0xFF2C2C2E)
val BorderLight = Color(0xFFC7C7CC)

// dynamic accessors (dipakai di seluruh ui)
var Background = DarkBackground
var Surface = DarkSurface
var SurfaceVariant = DarkSurfaceVariant
var SurfaceElevated = DarkSurfaceElevated
var TextPrimary = DarkTextPrimary
var TextSecondary = DarkTextSecondary
var LineNumber = DarkLineNumber
var Border = BorderDark
var TabActive = Primary
var TabInactive = DarkSurfaceElevated

fun applyTheme(isDark: Boolean) {
    if (isDark) {
        Background = DarkBackground
        Surface = DarkSurface
        SurfaceVariant = DarkSurfaceVariant
        SurfaceElevated = DarkSurfaceElevated
        TextPrimary = DarkTextPrimary
        TextSecondary = DarkTextSecondary
        LineNumber = DarkLineNumber
        Border = BorderDark
        TabInactive = DarkSurfaceElevated
    } else {
        Background = LightBackground
        Surface = LightSurface
        SurfaceVariant = LightSurfaceVariant
        SurfaceElevated = LightSurfaceElevated
        TextPrimary = LightTextPrimary
        TextSecondary = LightTextSecondary
        LineNumber = LightLineNumber
        Border = BorderLight
        TabInactive = LightSurfaceElevated
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error = Error
)

@Composable
fun CodeEditorTheme(isDark: Boolean = true, content: @Composable () -> Unit) {
    applyTheme(isDark)
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}
