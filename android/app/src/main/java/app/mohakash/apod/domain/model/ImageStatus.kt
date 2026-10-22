package app.mohakash.apod.domain.model

/**
 * Availability of the still image belonging to an entry.
 *
 * * [OK] – a real picture is available.
 * * [PLACEHOLDER] – the publisher substituted a generic placeholder picture.
 * * [MISSING] – no picture at all.
 *
 * Only [OK] images may be used as a live wallpaper; a placeholder or a missing image keeps the
 * previously working wallpaper in place.
 */
enum class ImageStatus {
    OK,
    PLACEHOLDER,
    MISSING;

    /** True when the picture is a real photograph/illustration. */
    val isUsable: Boolean get() = this == OK

    companion object {
        fun from(raw: String?): ImageStatus = when (raw?.trim()?.lowercase()) {
            "placeholder" -> PLACEHOLDER
            "missing" -> MISSING
            "ok", "available", "ready" -> OK
            else -> OK
        }
    }
}
