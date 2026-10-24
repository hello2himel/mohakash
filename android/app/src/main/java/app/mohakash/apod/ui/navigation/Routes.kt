package app.mohakash.apod.ui.navigation

/** The destinations of the app. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val ARCHIVE = "archive"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val ADVANCED = "settings/advanced"
    const val DETAIL = "detail/{date}"

    /** The detail screen of one entry. */
    fun detail(date: String): String = "detail/$date"
}
