package com.autominimal.launcher.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.R
import com.autominimal.launcher.data.AppFont

/** Palette from the design canvas. */
object C {
    /** Page background of settings and All apps. */
    val Ground = Color(0xFF0F0F0E)
    /** Cards, sheets and the search field. */
    val Card = Color(0xFF1B1A18)
    /** Selected options and boxes inside cards. */
    val Raised = Color(0xFF262522)
    /** Unselected tiles in the block editor. */
    val Sunken = Color(0xFF161614)
    val Avatar = Color(0xFF33302B)
    /** Hairline between list rows on a page. */
    val Hair = Color(0xFF1F1E1C)
    /** Hairline between rows inside a sheet. */
    val SheetLine = Color(0xFF2A2926)
    val Outline = Color(0xFF2E2D2A)
    val Border = Color(0xFF3A3935)

    val Text = Color(0xFFEDEAE3)
    val Soft = Color(0xFFBDB8AE)
    val Sub = Color(0xFF9A968D)
    val Muted = Color(0xFF8A867E)
    val Faint = Color(0xFF6E6A63)
    val Dim = Color(0xFF55524C)

    val Accent = Color(0xFFE8B27A)
    val Green = Color(0xFF9FC2A8)
    val GreenDeep = Color(0xFF26332B)
    val Purple = Color(0xFFB8A9D9)
    val Blue = Color(0xFF8DB8D6)
    val Danger = Color(0xFFF08C78)

    /** Text drawn over the wallpaper. */
    val Home = Color(0xFFF4EFE8)
    val HomeSoft = Home.copy(alpha = 0.78f)
    val HomeMuted = Home.copy(alpha = 0.62f)
    val HomeTrack = Home.copy(alpha = 0.16f)
    /** Scrim behind sheets on the home screen. */
    val Scrim = Color(0xB80A0908)

    /** Block colours, in slot order. Calendar and weather blocks are always [Blue]. */
    val Regions = listOf(Accent, Green, Purple, Blue)

    /** Calendar colours, in calendar-list order. */
    val Calendars = listOf(Accent, Blue, Purple, Green, Danger)
}

val InstrumentSans = FontFamily(
    Font(R.font.instrumentsans_regular, FontWeight.Normal),
    Font(R.font.instrumentsans_medium, FontWeight.Medium),
    Font(R.font.instrumentsans_semibold, FontWeight.SemiBold),
)

val PlexSans = FontFamily(
    Font(R.font.ibmplexsans_regular, FontWeight.Normal),
    Font(R.font.ibmplexsans_medium, FontWeight.Medium),
    Font(R.font.ibmplexsans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibmplexmono_light, FontWeight.Light),
    Font(R.font.ibmplexmono_regular, FontWeight.Normal),
    Font(R.font.ibmplexmono_medium, FontWeight.Medium),
)

val Newsreader = FontFamily(Font(R.font.newsreader_regular, FontWeight.Normal))
val RobotoCondensed = FontFamily(Font(R.font.robotocondensed_regular, FontWeight.Normal))
val JetBrainsMono = FontFamily(Font(R.font.jetbrainsmono_regular, FontWeight.Normal))

val Symbols = FontFamily(Font(R.font.material_symbols))
val SymbolsFilled = FontFamily(Font(R.font.material_symbols_filled))

/** Typeface chosen in settings. */
val LocalAppFont = staticCompositionLocalOf<FontFamily> { InstrumentSans }

fun AppFont.family(): FontFamily = when (this) {
    AppFont.SYSTEM -> InstrumentSans
    AppFont.PLEX -> PlexSans
    AppFont.PLEX_MONO -> PlexMono
    AppFont.SERIF -> Newsreader
    AppFont.CONDENSED -> RobotoCondensed
    AppFont.MONO -> JetBrainsMono
}

/** Line boxes as tight as CSS `line-height: normal`, so sizes from the design add up. */
val Tight = PlatformTextStyle(includeFontPadding = false)
val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Every Material text style in the chosen face, so home, menus, and settings share it. */
private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun LauncherTheme(font: AppFont, content: @Composable () -> Unit) {
    val family = font.family()
    val typography = remember(family) { Typography().withFamily(family) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = C.Card, background = C.Ground, surface = C.Card,
            onSurface = C.Text, onBackground = C.Text, outline = C.Border, surfaceContainer = C.Card,
        ),
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalAppFont provides family,
            LocalTextStyle provides typography.bodyLarge.copy(color = C.Text, platformStyle = Tight, lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp),
            content = content,
        )
    }
}
