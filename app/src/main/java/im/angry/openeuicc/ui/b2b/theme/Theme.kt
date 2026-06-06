package im.angry.openeuicc.ui.b2b.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = B2BPrimary,
    secondary = B2BSecondary,
    tertiary = B2BPrimaryVariant,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = B2BOnPrimary,
    onSecondary = B2BOnSecondary,
    onBackground = Color(0xFFE1E1E1),
    onSurface = Color(0xFFE1E1E1)
)

private val LightColorScheme = lightColorScheme(
    primary = B2BPrimary,
    secondary = B2BSecondary,
    tertiary = B2BPrimaryVariant,
    background = B2BBackground,
    surface = B2BSurface,
    onPrimary = B2BOnPrimary,
    onSecondary = B2BOnSecondary,
    onBackground = B2BOnBackground,
    onSurface = B2BOnSurface
)

@Composable
fun Roam2WorldB2BTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
