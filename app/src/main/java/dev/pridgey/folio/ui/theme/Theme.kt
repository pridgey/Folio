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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.R
import dev.pridgey.folio.data.Accent
import dev.pridgey.folio.data.ThemeMode

/** Folio's palette: ink and paper neutrals so covers carry the colour, and one accent (often taken from the cover). */
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
        // Ink: near-black with a hint of warmth, so cover colour reads as the only colour.
        val ink = Color(0xFFF3F0EA)
        FolioColors(
            isDark = true,
            background = Color(0xFF0A0A0B),
            grouped = Color(0xFF0A0A0B),
            surface = Color(0xFF151517),
            elevated = Color(0xFF1B1B1E),
            fill = ink.copy(alpha = 0.075f),
            separator = ink.copy(alpha = 0.09f),
            label = ink,
            secondaryLabel = ink.copy(alpha = 0.6f),
            tertiaryLabel = ink.copy(alpha = 0.34f),
            accent = a,
            onAccent = onAccent,
            accentSoft = a.copy(alpha = 0.16f),
            success = Color(0xFF5BC98B),
            warning = Color(0xFFF2B54A),
            danger = Color(0xFFF2685A),
            placeholder = Color(0xFF17171A),
            coverStroke = Color.White.copy(alpha = 0.07f),
            glass = Color(0xFF111113).copy(alpha = 0.74f),
        )
    } else {
        // Paper: a warm off-white page with ink-coloured text.
        val ink = Color(0xFF16140F)
        FolioColors(
            isDark = false,
            background = Color(0xFFF7F5F0),
            grouped = Color(0xFFF7F5F0),
            surface = Color(0xFFFFFEFB),
            elevated = Color(0xFFFFFEFB),
            fill = ink.copy(alpha = 0.06f),
            separator = ink.copy(alpha = 0.09f),
            label = ink,
            secondaryLabel = ink.copy(alpha = 0.6f),
            tertiaryLabel = ink.copy(alpha = 0.34f),
            accent = a,
            onAccent = onAccent,
            accentSoft = a.copy(alpha = 0.12f),
            success = Color(0xFF2E9A5E),
            warning = Color(0xFFD98A12),
            danger = Color(0xFFD5432F),
            placeholder = Color(0xFFEAE6DE),
            coverStroke = Color.Black.copy(alpha = 0.07f),
            glass = Color(0xFFFBFAF6).copy(alpha = 0.8f),
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

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Archivo, condensed: Folio's poster-like voice for titles. */
val Display = FontFamily(archivo(700, 78f), archivo(800, 74f), archivo(900, 72f))

/** Archivo at a near-normal width, for headings that sit with body text. */
val Heading = FontFamily(archivo(600, 92f), archivo(700, 90f))

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, size: Int, line: Int, weight: FontWeight, tracking: Double) = TextStyle(
    fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.em, lineHeightStyle = trim,
)

/** Archivo for titles and headings; Inter for everything you read. */
@Immutable
object FolioType {
    val largeTitle = style(Display, 42, 44, FontWeight.ExtraBold, -0.01)
    val title1 = style(Display, 32, 34, FontWeight.ExtraBold, -0.005)
    val title2 = style(Heading, 23, 28, FontWeight.Bold, -0.01)
    val title3 = style(Heading, 20, 25, FontWeight.Bold, -0.008)
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
    val sectionHeader = style(Heading, 17, 22, FontWeight.Bold, -0.005)
    /** Small spaced capitals above a title ("CONTINUE · CH 141"). */
    val eyebrow = style(Inter, 11, 14, FontWeight.SemiBold, 0.08)
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
