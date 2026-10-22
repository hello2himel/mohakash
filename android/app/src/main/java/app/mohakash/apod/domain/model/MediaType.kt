package app.mohakash.apod.domain.model

/**
 * The kind of media an APOD entry carries.
 *
 * The public API reports `image`, `video` or `other`. `other` covers entries that embed an
 * iframe (interactive 3D scenes, panoramas, ...). Both [VIDEO] and [OTHER] entries can still
 * provide a static poster image which is what the live wallpaper falls back to.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    OTHER;

    /** True when the entry carries a still picture that can be used as a wallpaper. */
    val isImage: Boolean get() = this == IMAGE

    /** True when the entry is played elsewhere and only a poster/thumbnail is available. */
    val isPlayable: Boolean get() = this == VIDEO || this == OTHER

    companion object {
        fun from(raw: String?): MediaType = when (raw?.trim()?.lowercase()) {
            "image" -> IMAGE
            "video" -> VIDEO
            "other", "iframe" -> OTHER
            else -> OTHER
        }
    }
}
