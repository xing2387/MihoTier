package top.xing2387.mihotier.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = ClashPrimary,
    onPrimary = Color.White,
    secondary = ClashAccent,
    background = ClashBackground,
    surface = ClashSurface,
    onBackground = ClashTextPrimary,
    onSurface = ClashTextPrimary,
    outlineVariant = ClashDivider
)

// For now, we use light scheme for both to closely match screenshots unless dark mode is heavily desired.
// If dark mode is needed, we would define a DarkColorScheme.
private val DarkColorScheme = darkColorScheme(
    primary = ClashPrimary,
    onPrimary = Color.White,
    secondary = ClashAccent,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    outlineVariant = Color(0xFF333333)
)

@Composable
fun MihotierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled dynamic color to match screenshots
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}