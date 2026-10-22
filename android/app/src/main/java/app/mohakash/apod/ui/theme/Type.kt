package app.mohakash.apod.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mohakash.apod.R

/**
 * Newsreader is used for every title, Inter for the user interface and the body copy.
 *
 * Both families are bundled as real font files, so the app does not depend on the downloadable
 * fonts service. If a font ever fails to load, Android falls back to the platform serif
 * (`FontFamily.Serif`) and sans-serif (`FontFamily.SansSerif`) automatically.
 */
val NewsreaderFontFamily = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Medium),
)

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/**
 * The named text styles of the app.
 */
object MohakashTextStyles {

    /** The page title — 26 / 1.2, Newsreader. */
    val PageTitle = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 31.sp,
    )

    /** The screen title in the top bar — 22, Newsreader. */
    val ScreenTitle = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** The title of a sheet — 24, Newsreader. */
    val SheetTitle = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    )

    /** The onboarding headline — 30 / 1.15, Newsreader. */
    val OnboardingTitle = TextStyle(
        fontFamily = NewsreaderFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 35.sp,
    )

    /** A section heading — 18 / 600, Inter. */
    val SectionHeading = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )

    /** Body copy — 16 / 1.65, Inter. */
    val BodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    )

    /** Body copy — 15 / 1.65, Inter. */
    val BodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 25.sp,
    )

    /** User interface text — 15 / 500, Inter. */
    val UiMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** User interface text — 14 / 500, Inter. */
    val UiSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    /** A caption — 12, Inter. */
    val Caption = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** A caption that is used for a section label — 12, Inter. */
    val CaptionMuted = Caption.copy(color = MohakashColors.TextMuted)
}

/**
 * Maps the named styles onto the Material 3 typography so the built-in components look right.
 */
val MohakashTypography = Typography(
    displayLarge = MohakashTextStyles.OnboardingTitle,
    displayMedium = MohakashTextStyles.OnboardingTitle,
    displaySmall = MohakashTextStyles.PageTitle,
    headlineLarge = MohakashTextStyles.PageTitle,
    headlineMedium = MohakashTextStyles.PageTitle,
    headlineSmall = MohakashTextStyles.SheetTitle,
    titleLarge = MohakashTextStyles.ScreenTitle,
    titleMedium = MohakashTextStyles.SectionHeading,
    titleSmall = MohakashTextStyles.UiSmall,
    bodyLarge = MohakashTextStyles.BodyLarge,
    bodyMedium = MohakashTextStyles.BodyMedium,
    bodySmall = MohakashTextStyles.Caption,
    labelLarge = MohakashTextStyles.UiMedium,
    labelMedium = MohakashTextStyles.UiSmall,
    labelSmall = MohakashTextStyles.Caption,
)
