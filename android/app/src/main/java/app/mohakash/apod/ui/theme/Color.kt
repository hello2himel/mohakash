package app.mohakash.apod.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Mohakash colour palette.
 *
 * The app is dark only — there is no light theme on purpose.
 */
object MohakashColors {

    /** The page background. */
    val Background = Color(0xFF0B0D12)

    /** Cards, sheets and dialogs. */
    val Surface = Color(0xFF151922)

    /** The bottom navigation bar. */
    val SurfaceRaised = Color(0xFF11141B)

    /** Notes and highlighted surfaces. */
    val SurfaceStrong = Color(0xFF1B2030)

    /** Hairlines around images and inputs. */
    val Border = Color(0xFF232838)

    /** Hairlines around chips and segmented controls. */
    val BorderStrong = Color(0xFF323849)

    /** Separators between list rows. */
    val Divider = Color(0xFF1B2030)

    /** Headlines and primary text. */
    val Text = Color(0xFFE8EAF0)

    /** Body copy. */
    val TextBody = Color(0xFFD3D8E3)

    /** The three-line explanation preview on the Today screen. */
    val TextPreview = Color(0xFFC5CBD9)

    /** Secondary text, captions, disabled states. */
    val TextMuted = Color(0xFFA3ABBD)

    /** The single accent colour of the app. */
    val Accent = Color(0xFF8FB0FF)

    /** The lit and shaded ends of the random key's blue gradient. */
    val AccentLight = Color(0xFFB6CBFF)
    val AccentDeep = Color(0xFF6C8DE0)

    /** Text and icons on top of [Accent]. */
    val OnAccent = Color(0xFF0B0D12)

    /** Validation errors and other non-destructive failures. */
    val Error = Color(0xFFFF8A8A)

    /** The dim layer behind sheets and dialogs — `rgba(0, 0, 0, 0.62)`. */
    val Scrim = Color(0x9E000000)
}
