package app.otakureader.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Default Material 3 colors (kept for backwards compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// Pure Black for AMOLED mode
val PureBlack = Color(0xFF000000)

// Green Apple theme colors
private val GreenAppleLight = lightColorScheme(
    primary = Color(0xFF3A7D44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F4BD),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E8CF),
    onSecondaryContainer = Color(0xFF0F1F0F),
)

private val GreenAppleDark = darkColorScheme(
    primary = Color(0xFF9DD7A3),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF1F5129),
    onPrimaryContainer = Color(0xFFB8F4BD),
    secondary = Color(0xFFB8CCB4),
    onSecondary = Color(0xFF233423),
    secondaryContainer = Color(0xFF3A4B38),
    onSecondaryContainer = Color(0xFFD4E8CF),
)

// Lavender theme colors
private val LavenderLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
)

private val LavenderDark = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
)

// Midnight Dusk theme colors
private val MidnightDuskLight = lightColorScheme(
    primary = Color(0xFF4A5C92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001944),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
)

private val MidnightDuskDark = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF2F4578),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
)

// Strawberry Daiquiri theme colors
private val StrawberryDaiquiriLight = lightColorScheme(
    primary = Color(0xFFC00055),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E0019),
    secondary = Color(0xFF75565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
)

private val StrawberryDaiquiriDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF65002C),
    primaryContainer = Color(0xFF8E0040),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE3BDC8),
    onSecondary = Color(0xFF432931),
    secondaryContainer = Color(0xFF5C3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
)

// Tako (Grey/Purple) theme colors
private val TakoLight = lightColorScheme(
    primary = Color(0xFF66577E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF211637),
    secondary = Color(0xFF625B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF1E192A),
)

private val TakoDark = darkColorScheme(
    primary = Color(0xFFD0BCFE),
    onPrimary = Color(0xFF382C4D),
    primaryContainer = Color(0xFF4E4265),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCDC2DB),
    onSecondary = Color(0xFF332E40),
    secondaryContainer = Color(0xFF4A4557),
    onSecondaryContainer = Color(0xFFE9DEF8),
)

// Teal & Turquoise theme colors
private val TealTurquoiseLight = lightColorScheme(
    primary = Color(0xFF006A67),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF72F7F2),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E6),
    onSecondaryContainer = Color(0xFF051F1F),
)

private val TealTurquoiseDark = darkColorScheme(
    primary = Color(0xFF51DAD5),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = Color(0xFF72F7F2),
    secondary = Color(0xFFB1CCCB),
    onSecondary = Color(0xFF1C3534),
    secondaryContainer = Color(0xFF324B4A),
    onSecondaryContainer = Color(0xFFCCE8E6),
)

// Tidal Wave (Blue) theme colors
private val TidalWaveLight = lightColorScheme(
    primary = Color(0xFF0061A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0E1D2A),
)

private val TidalWaveDark = darkColorScheme(
    primary = Color(0xFF9BCBFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF24323F),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E4F7),
)

// Yotsuba (Green/Yellow) theme colors
private val YotsubaLight = lightColorScheme(
    primary = Color(0xFF606A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F267),
    onPrimaryContainer = Color(0xFF1C2000),
    secondary = Color(0xFF5E6043),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E4C0),
    onSecondaryContainer = Color(0xFF1A1D06),
)

private val YotsubaDark = darkColorScheme(
    primary = Color(0xFFC9D54E),
    onPrimary = Color(0xFF303700),
    primaryContainer = Color(0xFF474F00),
    onPrimaryContainer = Color(0xFFE6F267),
    secondary = Color(0xFFC7C8A5),
    onSecondary = Color(0xFF2F321A),
    secondaryContainer = Color(0xFF46482D),
    onSecondaryContainer = Color(0xFFE3E4C0),
)

// Yin & Yang (Black & White) theme colors
private val YinYangLight = lightColorScheme(
    primary = Color(0xFF5F5E62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E1E6),
    onPrimaryContainer = Color(0xFF1B1B1F),
    secondary = Color(0xFF5F5D62),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E1E6),
    onSecondaryContainer = Color(0xFF1B1B1E),
)

private val YinYangDark = darkColorScheme(
    primary = Color(0xFFC8C5CA),
    onPrimary = Color(0xFF303034),
    primaryContainer = Color(0xFF47464A),
    onPrimaryContainer = Color(0xFFE5E1E6),
    secondary = Color(0xFFC8C5CA),
    onSecondary = Color(0xFF302F33),
    secondaryContainer = Color(0xFF464549),
    onSecondaryContainer = Color(0xFFE4E1E6),
)

// Map of color schemes
internal val ColorSchemes = mapOf(
    2 to (GreenAppleLight to GreenAppleDark),
    3 to (LavenderLight to LavenderDark),
    4 to (MidnightDuskLight to MidnightDuskDark),
    5 to (StrawberryDaiquiriLight to StrawberryDaiquiriDark),
    6 to (TakoLight to TakoDark),
    7 to (TealTurquoiseLight to TealTurquoiseDark),
    8 to (TidalWaveLight to TidalWaveDark),
    9 to (YotsubaLight to YotsubaDark),
    10 to (YinYangLight to YinYangDark),
)
