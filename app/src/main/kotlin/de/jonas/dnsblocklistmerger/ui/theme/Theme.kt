package de.jonas.dnsblocklistmerger.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Material 3 theme; [dynamic] uses the wallpaper colours of Android, previews turn it off. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(LocalContext.current)
        dynamic -> dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

enum class PillKind { OK, WARN, ERROR, NEUTRAL }

@Composable
fun Pill(text: String, kind: PillKind) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (bg, fg) = when (kind) {
        PillKind.OK -> if (dark) Color(0xFF1E4620) to Color(0xFFB8F5B0) else Color(0xFFDDF4DA) to Color(0xFF1B5E20)
        PillKind.WARN -> if (dark) Color(0xFF4A3B00) to Color(0xFFFFE08A) else Color(0xFFFFF1C2) to Color(0xFF6B4E00)
        PillKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        PillKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
