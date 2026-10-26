package app.mohakash.apod.ui.detail

import kotlin.math.max
import kotlin.math.min

/**
 * The geometry of the uncropped picture in the entry viewer.
 *
 * Everything in this file is pure maths on pixels/floats. Keeping it out of the composable makes
 * the two promises of the viewer testable:
 *
 * * the picture is never cropped — it is always drawn in full inside its box with `Fit`;
 * * the picture is never taller than [EntryImageSizing.MAX_HEIGHT_FRACTION] of the viewport, so
 *   the title, the actions and the explanation stay reachable with a single short scroll.
 */
object EntryImageSizing {

    /** The fallback width/height ratio used before the real picture size is known. */
    const val DEFAULT_ASPECT_RATIO: Float = 3f / 2f

    /** The tallest the picture box may be, as a fraction of the content viewport height. */
    const val MAX_HEIGHT_FRACTION: Float = 0.62f

    /** The scale limits of the pinch/double-tap zoom. */
    const val MIN_ZOOM: Float = 1f
    const val MAX_ZOOM: Float = 4f

    /** The scale double tap zooms in to. */
    const val DOUBLE_TAP_ZOOM: Float = 2.5f

    /** Anything above this is considered zoomed (and gets a reset affordance). */
    const val ZOOMED_THRESHOLD: Float = 1.01f

    /** Sanity bounds for an aspect ratio; an uncropped picture may still be fitted inside. */
    private const val MIN_ASPECT_RATIO = 0.1f
    private const val MAX_ASPECT_RATIO = 20f

    private const val MIN_BOX_HEIGHT = 1f

    /**
     * Returns a usable width/height ratio.
     *
     * Unloaded pictures report [Float.NaN] or `0`, an absurd ratio is clamped; the picture is
     * still fitted, never cropped, so this only avoids degenerate layout sizes.
     */
    fun sanitizeAspectRatio(aspectRatio: Float): Float {
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) return DEFAULT_ASPECT_RATIO
        return aspectRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
    }

    /**
     * The box the picture is drawn into: always the full viewport width, and as tall as the
     * aspect ratio needs — capped at [maxHeightFraction] of the viewport.
     *
     * When the cap is hit the picture is letterboxed inside the box; it is never cropped.
     */
    fun imageBox(
        viewportWidth: Float,
        viewportHeight: Float,
        aspectRatio: Float,
        maxHeightFraction: Float = MAX_HEIGHT_FRACTION,
    ): EntryImageBox {
        if (!viewportWidth.isFinite() || viewportWidth <= 0f) return EntryImageBox(0f, 0f)
        val ratio = sanitizeAspectRatio(aspectRatio)
        val naturalHeight = viewportWidth / ratio
        val cap = if (viewportHeight.isFinite() && viewportHeight > 0f) {
            viewportHeight * maxHeightFraction.coerceIn(0.1f, 1f)
        } else {
            naturalHeight
        }
        return EntryImageBox(
            width = viewportWidth,
            height = min(naturalHeight, cap).coerceAtLeast(MIN_BOX_HEIGHT),
        )
    }

    /**
     * The size the whole picture occupies inside [boxWidth] × [boxHeight] with `ContentScale.Fit`.
     *
     * The whole picture is drawn: at least one dimension touches the box, the other is
     * letterboxed.
     */
    fun fitContent(boxWidth: Float, boxHeight: Float, aspectRatio: Float): EntryContentSize {
        if (!boxWidth.isFinite() || !boxHeight.isFinite() || boxWidth <= 0f || boxHeight <= 0f) {
            return EntryContentSize(0f, 0f)
        }
        val ratio = sanitizeAspectRatio(aspectRatio)
        val boxRatio = boxWidth / boxHeight
        return if (ratio >= boxRatio) {
            // The picture is wider than the box: it is limited by the width.
            EntryContentSize(width = boxWidth, height = boxWidth / ratio)
        } else {
            // The picture is taller than the box: it is limited by the height.
            EntryContentSize(width = boxHeight * ratio, height = boxHeight)
        }
    }

    /** Clamps a zoom scale into [MIN_ZOOM] … [MAX_ZOOM]; invalid input falls back to 1×. */
    fun clampScale(scale: Float): Float {
        if (!scale.isFinite()) return MIN_ZOOM
        return scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** The scale a double tap should animate to: 2.5× when not zoomed, 1× (reset) when zoomed. */
    fun doubleTapScale(currentScale: Float): Float {
        return if (isZoomed(currentScale)) MIN_ZOOM else DOUBLE_TAP_ZOOM
    }

    /** True when the picture is zoomed enough for a reset affordance to be useful. */
    fun isZoomed(scale: Float): Boolean = clampScale(scale) > ZOOMED_THRESHOLD

    /**
     * How far the zoomed picture may be panned before its edges leave the box.
     *
     * At 1× every bound is 0, so a non-zoomed picture can never be dragged around.
     */
    fun panBounds(
        boxWidth: Float,
        boxHeight: Float,
        content: EntryContentSize,
        scale: Float,
    ): EntryPanBounds {
        val safeScale = clampScale(scale)
        val overflowX = max(0f, content.width * safeScale - boxWidth)
        val overflowY = max(0f, content.height * safeScale - boxHeight)
        return EntryPanBounds(maxX = overflowX / 2f, maxY = overflowY / 2f)
    }

    /** Clamps a pan offset so the zoomed picture always covers its box. */
    fun clampPan(x: Float, y: Float, bounds: EntryPanBounds): EntryPan {
        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        return EntryPan(
            x = safeX.coerceIn(-bounds.maxX, bounds.maxX),
            y = safeY.coerceIn(-bounds.maxY, bounds.maxY),
        )
    }
}

/** The picture box: full width, aspect-true height, capped by the viewport. */
data class EntryImageBox(val width: Float, val height: Float)

/** The size the whole picture occupies inside its box under `ContentScale.Fit`. */
data class EntryContentSize(val width: Float, val height: Float)

/** The largest pan offsets, measured from the centre, in pixels. */
data class EntryPanBounds(val maxX: Float, val maxY: Float)

/** A clamped pan offset in pixels. */
data class EntryPan(val x: Float, val y: Float)
