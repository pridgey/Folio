package dev.pridgey.folio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.R
import dev.pridgey.folio.data.Accent
import dev.pridgey.folio.data.ThemeMode

/** Folio's palette: neutral, quiet surfaces so covers carry the colour, and one accent. */
@Immutable
data class FolioColors(
    val isDark: Boolean,
    val background: Color,
    /** Behind grouped (inset) lists. */
    val grouped: Color,
    /** Cards and grouped rows. */
    val surface: Color,
    /** Sheets and menus that float. */
    val elevated: Color,
    /** Control fills: search fields, segmented controls, chips. */
    val fill: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val placeholder: Color,
    /** Hairline around covers so light art doesn't melt into a light background. */
    val coverStroke: Color,
    /** The tint of glass bars. */
    val glass: Color,
)

private fun accentColor(accent: Accent, dark: Boolean): Color = when (accent) {
    Accent.PERSIMMON -> if (dark) Color(0xFFFF6A3D) else Color(0xFFE8501F)
    Accent.INDIGO -> if (dark) Color(0xFF7D7AFF) else Color(0xFF5856D6)
    Accent.SAKURA -> if (dark) Color(0xFFFF6B9A) else Color(0xFFE5467A)
    Accent.MATCHA -> if (dark) Color(0xFF4CD48A) else Color(0xFF1FA463)
    Accent.GRAPHITE -> if (dark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)
}

fun folioColors(dark: Boolean, accent: Accent): FolioColors {
    val a = accentColor(accent, dark)
    val onAccent = if (accent == Accent.GRAPHITE && dark) Color.Black else Color.White
    return if (dark) {
        FolioColors(
            isDark = true,
            background = Color(0xFF000000),
            grouped = Color(0xFF000000),
            surface = Color(0xFF1C1C1E),
            elevated = Color(0xFF242426),
            fill = Color(0xFF767680).copy(alpha = 0.24f),
            separator = Color(0xFF545458).copy(alpha = 0.6f),
            label = Color(0xFFFFFFFF),
            secondaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.62f),
            tertiaryLabel = Color(0xFFEBEBF5).copy(alpha = 0.32f),
            accent = a,
            onAccent = onAccent,
            accentSoft = a.copy(alpha = 0.18f),
            success = Color(0xFF30D158),
            warning = Color(0xFFFFB340),
            danger = Color(0xFFFF453A),
            placeholder = Color(0xFF1C1C1E),
            coverStroke = Color.White.copy(alpha = 0.08f),
            glass = Color(0xFF101012).copy(alpha = 0.72f),
        )
    } else {
        FolioColors(
            isDark = false,
            background = Color(0xFFFFFFFF),
            grouped = Color(0xFFF2F2F7),
            surface = Color(0xFFFFFFFF),
            elevated = Color(0xFFFFFFFF),
            fill = Color(0xFF767680).copy(alpha = 0.12f),
            separator = Color(0xFF3C3C43).copy(alpha = 0.22f),
            label = Color(0xFF000000),
            secondaryLabel = Color(0xFF3C3C43).copy(alpha = 0.62f),
            tertiaryLabel = Color(0xFF3C3C43).copy(alpha = 0.3f),
            accent = a,
            onAccent = onAccent,
            accentSoft = a.copy(alpha = 0.12f),
            success = Color(0xFF34C759),
            warning = Color(0xFFFF9500),
            danger = Color(0xFFFF3B30),
            placeholder = Color(0xFFEDEDF0),
            coverStroke = Color.Black.copy(alpha = 0.07f),
            glass = Color(0xFFF9F9FB).copy(alpha = 0.78f),
        )
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** The display cut of Inter, tighter, for big titles. */
val InterDisplay = FontFamily(
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold),
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.em, lineHeightStyle = trim,
)

/** A type scale modelled on the one people already read comfortably on phones. */
@Immutable
object FolioType {
    val largeTitle = style(InterDisplay, 34, 41, FontWeight.Bold, -0.022)
    val title1 = style(InterDisplay, 28, 34, FontWeight.Bold, -0.02)
    val title2 = style(Inter, 22, 28, FontWeight.Bold, -0.017)
    val title3 = style(Inter, 20, 25, FontWeight.SemiBold, -0.015)
    val headline = style(Inter, 17, 22, FontWeight.SemiBold, -0.012)
    val body = style(Inter, 17, 22, FontWeight.Normal, -0.012)
    val callout = style(Inter, 16, 21, FontWeight.Normal, -0.01)
    val subhead = style(Inter, 15, 20, FontWeight.Normal, -0.008)
    val subheadStrong = style(Inter, 15, 20, FontWeight.SemiBold, -0.008)
    val footnote = style(Inter, 13, 18, FontWeight.Normal, -0.004)
    val footnoteStrong = style(Inter, 13, 18, FontWeight.SemiBold, -0.004)
    val caption = style(Inter, 12, 16, FontWeight.Normal, 0.0)
    val captionStrong = style(Inter, 12, 16, FontWeight.SemiBold, 0.0)
    val caption2 = style(Inter, 11, 13, FontWeight.Medium, 0.006)
    val sectionHeader = style(Inter, 13, 18, FontWeight.Normal, 0.0)
}

val LocalFolioColors = staticCompositionLocalOf { folioColors(dark = false, accent = Accent.PERSIMMON) }

object Folio {
    val colors: FolioColors @Composable get() = LocalFolioColors.current
    val type = FolioType
}

@Composable
fun FolioTheme(mode: ThemeMode, accent: Accent, forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = folioColors(dark, accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondary = c.accent, background = c.background, onBackground = c.label, surface = c.background, onSurface = c.label,
            surfaceVariant = c.surface, onSurfaceVariant = c.secondaryLabel, surfaceContainer = c.surface,
            surfaceContainerHigh = c.elevated, surfaceContainerHighest = c.elevated, surfaceContainerLow = c.surface,
            outline = c.separator, outlineVariant = c.separator, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent,
            secondary = c.accent, background = c.background, onBackground = c.label, surface = c.background, onSurface = c.label,
            surfaceVariant = c.grouped, onSurfaceVariant = c.secondaryLabel, surfaceContainer = c.surface,
            surfaceContainerHigh = c.elevated, surfaceContainerHighest = c.elevated, surfaceContainerLow = c.grouped,
            outline = c.separator, outlineVariant = c.separator, error = c.danger,
        )
    }
    val base = MaterialTheme.typography
    val typography = base.copy(
        displayLarge = FolioType.largeTitle, displayMedium = FolioType.title1, displaySmall = FolioType.title1,
        headlineLarge = FolioType.title1, headlineMedium = FolioType.title2, headlineSmall = FolioType.title3,
        titleLarge = FolioType.title3, titleMedium = FolioType.headline, titleSmall = FolioType.subheadStrong,
        bodyLarge = FolioType.body, bodyMedium = FolioType.subhead, bodySmall = FolioType.footnote,
        labelLarge = FolioType.subheadStrong, labelMedium = FolioType.footnoteStrong, labelSmall = FolioType.caption2,
    )
    CompositionLocalProvider(LocalFolioColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
