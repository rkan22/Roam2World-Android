package im.angry.openeuicc.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Secondary,
    onSecondary = Color.White,
    tertiary = Success,
    onTertiary = Color.White,
    background = Background,
    surface = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Danger,
    onError = Color.White,
    outline = Border
)

@Composable
fun R2WTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Şimdilik sadece Light mode odaklı tasarım sistemine göre devam ediyoruz
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
