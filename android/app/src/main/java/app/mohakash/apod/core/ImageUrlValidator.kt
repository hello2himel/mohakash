package app.mohakash.apod.core

/**
 * Validates that a URL really points at an image asset.
 *
 * The public API mixes two kinds of links: image assets (`…/photo.jpg?w=1600`) and article pages
 * (`https://science.nasa.gov/image-article/…`). Only the former may be downloaded, so the page URL
 * is rejected before it is ever handed to the image loader.
 */
object ImageUrlValidator {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

    private val PAGE_EXTENSIONS = setOf(
        "html", "htm", "xhtml", "php", "asp", "aspx", "jsp", "json", "xml", "pdf",
    )

    /** The lower-case file extension of the URL path, or `null` when there is none. */
    fun extensionOf(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = raw.substringBefore('?').substringBefore('#')
        val lastSegment = path.substringAfterLast('/')
        if (!lastSegment.contains('.')) return null
        val extension = lastSegment.substringAfterLast('.').lowercase()
        return extension.takeIf { it.isNotEmpty() && it.length <= 5 }
    }
    /** True when the URL is an absolute `https` link that points at a picture. */
    fun isValidImageUrl(url: String?): Boolean {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!raw.startsWith("https://", ignoreCase = true)) return false
        val afterScheme = raw.substringAfter("://", "")
        if (afterScheme.isBlank()) return false
        val host = afterScheme.substringBefore('/').substringBefore('?')
        if (host.isBlank()) return false

        val extension = extensionOf(raw) ?: return false
        if (extension in PAGE_EXTENSIONS) return false
        return extension in IMAGE_EXTENSIONS
    }

    /** True when the URL looks like an article/web page rather than an image. */
    fun looksLikePageUrl(url: String?): Boolean {
        val extension = extensionOf(url) ?: return false
        return extension in PAGE_EXTENSIONS
    }
}
