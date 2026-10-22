package app.mohakash.apod.domain.wallpaper

import app.mohakash.apod.domain.model.WallpaperScaling
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An integer rectangle. Pure Kotlin on purpose so the maths can be unit tested without Android.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    companion object {
        val EMPTY = PixelRect(0, 0, 0, 0)
    }
}

/**
 * The geometry behind the *Fill* and *Fit* wallpaper modes.
 *
 * * **Fill** – the picture covers the whole screen; the parts that do not fit are cropped away.
 * * **Fit**  – the whole picture stays visible; the free space is filled with the app background.
 */
object WallpaperScaling {

    /**
     * The part of the source picture that has to be drawn.
     *
     * For [WallpaperScaling.FILL] this is the centred crop, for [WallpaperScaling.FIT] the whole
     * picture.
     */
    fun computeSourceRect(
        sourceWidth: Int,
        sourceHeight: Int,
        destinationWidth: Int,
        destinationHeight: Int,
        mode: WallpaperScaling,
    ): PixelRect {
        if (sourceWidth <= 0 || sourceHeight <= 0 || destinationWidth <= 0 || destinationHeight <= 0) {
            return PixelRect.EMPTY
        }
        if (mode == WallpaperScaling.FIT) {
            return PixelRect(0, 0, sourceWidth, sourceHeight)
        }
        // Fill: keep the part of the picture that matches the destination aspect ratio.
        val sourceAspect = sourceWidth.toDouble() / sourceHeight.toDouble()
        val destinationAspect = destinationWidth.toDouble() / destinationHeight.toDouble()

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > destinationAspect) {
            // The picture is wider than the screen: crop the sides.
            cropHeight = sourceHeight
            cropWidth = (sourceHeight * destinationAspect).roundToInt().coerceIn(1, sourceWidth)
        } else {
            // The picture is taller than the screen: crop the top and the bottom.
            cropWidth = sourceWidth
            cropHeight = (sourceWidth / destinationAspect).roundToInt().coerceIn(1, sourceHeight)
        }
        val left = ((sourceWidth - cropWidth) / 2.0).roundToInt()
        val top = ((sourceHeight - cropHeight) / 2.0).roundToInt()
        return PixelRect(
            left = left.coerceIn(0, sourceWidth - 1),
            top = top.coerceIn(0, sourceHeight - 1),
            right = (left + cropWidth).coerceIn(1, sourceWidth),
            bottom = (top + cropHeight).coerceIn(1, sourceHeight),
        )
    }

    /**
     * The area of the screen the picture is drawn into.
     *
     * For [WallpaperScaling.FILL] that is the whole screen, for [WallpaperScaling.FIT] the
     * centred letterbox rectangle.
     */
    fun computeDestinationRect(
        sourceWidth: Int,
        sourceHeight: Int,
        destinationWidth: Int,
        destinationHeight: Int,
        mode: WallpaperScaling,
    ): PixelRect {
        if (sourceWidth <= 0 || sourceHeight <= 0 || destinationWidth <= 0 || destinationHeight <= 0) {
            return PixelRect.EMPTY
        }
        if (mode == WallpaperScaling.FILL) {
            return PixelRect(0, 0, destinationWidth, destinationHeight)
        }
        val scale = min(
            destinationWidth.toDouble() / sourceWidth.toDouble(),
            destinationHeight.toDouble() / sourceHeight.toDouble(),
        )
        val width = (sourceWidth * scale).roundToInt().coerceIn(1, destinationWidth)
        val height = (sourceHeight * scale).roundToInt().coerceIn(1, destinationHeight)
        val left = (destinationWidth - width) / 2
        val top = (destinationHeight - height) / 2
        return PixelRect(left, top, left + width, top + height)
    }

    /**
     * The `inSampleSize` value that keeps the decoded bitmap close to the screen size without
     * wasting memory on a huge picture.
     *
     * Sampling stops as soon as the picture would become smaller than the screen and never
     * produces a bitmap whose longest edge is longer than [maxDimension].
     */
    fun computeSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        destinationWidth: Int,
        destinationHeight: Int,
        maxDimension: Int = 2880,
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        val targetLong = max(destinationWidth.coerceAtLeast(1), destinationHeight.coerceAtLeast(1))
        var sample = 1
        while (sample < 64) {
            val next = sample * 2
            val nextLong = max(sourceWidth / next, sourceHeight / next)
            if (nextLong < targetLong) break
            if (nextLong <= maxDimension) return next
            sample = next
        }
        return sample
    }
}
