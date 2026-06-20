package im.angry.openeuicc.ui.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * Roam2World light-blue B2B design tokens.
 * Keep these values aligned with the Figma file:
 * Roam2World B2B Light Blue UI Kit + 19 Screens
 */
val Primary = Color(0xFF006BFF)
val PrimaryDark = Color(0xFF0047B3)
val PrimaryLight = Color(0xFFEAF4FF)
val Secondary = Color(0xFF11B8FF)
val Success = Color(0xFF12B76A)
val Warning = Color(0xFFF79009)
val Danger = Color(0xFFF04438)
val Background = Color(0xFFF6FAFF)
val CardWhite = Color(0xFFFFFFFF)
val Border = Color(0xFFD9E6F7)
val TextPrimary = Color(0xFF07152F)
val TextSecondary = Color(0xFF667085)
val MutedSurface = Color(0xFFF2F7FF)
val DividerBlue = Color(0xFFDCEEFF)

object R2WColors {
    val Blue = Primary
    val BlueDark = PrimaryDark
    val Sky = PrimaryLight
    val SkySoft = MutedSurface
    val Surface = CardWhite
    val Background = im.angry.openeuicc.ui.compose.theme.Background
    val TextDark = TextPrimary
    val TextMuted = TextSecondary
    val Border = im.angry.openeuicc.ui.compose.theme.Border
    val Success = im.angry.openeuicc.ui.compose.theme.Success
    val Warning = im.angry.openeuicc.ui.compose.theme.Warning
    val Danger = im.angry.openeuicc.ui.compose.theme.Danger
}
