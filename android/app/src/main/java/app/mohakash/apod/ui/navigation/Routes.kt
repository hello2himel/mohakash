package app.mohakash.apod.ui.navigation

/**
 * The destinations of the app.
 *
 * `TODAY` keeps its historical route string even though the screen is now the combined feed — the
 * start destination, the saved tab state and the public deep links all depend on it.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today" // now hosts the combined feed
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val ADVANCED = "settings/advanced"
    const val DETAIL = "detail/{date}"

    /** The detail screen of one entry. */
    fun detail(date: String): String = "detail/$date"
}
