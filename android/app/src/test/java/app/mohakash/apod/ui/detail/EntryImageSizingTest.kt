package app.mohakash.apod.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The uncropped-picture promises of the entry viewer are pure maths; these tests pin them down.
 */
class EntryImageSizingTest {

    private val viewportWidth = 390f
    private val viewportHeight = 844f
    private val epsilon = 0.01f

    @Test
    fun `a landscape picture is as tall as its ratio needs`() {
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, 3f / 2f)

        assertEquals(viewportWidth, box.width, epsilon)
        assertEquals(260f, box.height, epsilon)
    }

    @Test
    fun `a portrait picture is capped at 62 percent of the viewport and narrows`() {
        val ratio = 2f / 3f
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, ratio)
        val cap = viewportHeight * EntryImageSizing.MAX_HEIGHT_FRACTION

        assertEquals(cap * ratio, box.width, epsilon)
        assertEquals(cap, box.height, epsilon)
        assertTrue(box.width < viewportWidth)
        assertTrue(box.height < viewportWidth / ratio)
    }

    @Test
    fun `a panorama keeps its real ratio and stays a short strip`() {
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, 4f)

        assertEquals(viewportWidth, box.width, epsilon)
        assertEquals(97.5f, box.height, epsilon)
    }

    @Test
    fun `a tall picture shrinks its width instead of becoming a letterboxed box`() {
        val ratio = 2f / 3f
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, ratio)
        val content = EntryImageSizing.fitContent(box.width, box.height, ratio)

        // The box itself is the picture's aspect-true size: no side letterboxing.
        assertEquals(box.width, content.width, epsilon)
        assertEquals(box.height, content.height, epsilon)
        assertTrue(box.width < viewportWidth)
    }

    @Test
    fun `wide content is width-limited and letterboxed top and bottom`() {
        // A 2:1 picture inside a relatively tall box keeps its width, letterboxes vertically.
        val content = EntryImageSizing.fitContent(boxWidth = 390f, boxHeight = 523f, aspectRatio = 2f)

        assertEquals(390f, content.width, epsilon)
        assertEquals(195f, content.height, epsilon)
        assertTrue(content.height < 523f)
    }

    @Test
    fun `the whole picture is visible at every ratio`() {
        val ratios = listOf(0.4f, 0.75f, 1f, 1.33f, 1.5f, 1.78f, 2.39f, 3f, 4f)
        ratios.forEach { ratio ->
            val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, ratio)
            val content = EntryImageSizing.fitContent(box.width, box.height, ratio)

            assertTrue("width overflow at $ratio", content.width <= box.width + epsilon)
            assertTrue("height overflow at $ratio", content.height <= box.height + epsilon)
            val touchesWidth = kotlin.math.abs(content.width - box.width) <= epsilon
            val touchesHeight = kotlin.math.abs(content.height - box.height) <= epsilon
            assertTrue("content does not touch the box at $ratio", touchesWidth || touchesHeight)
        }
    }

    @Test
    fun `invalid aspect ratios fall back to the default instead of a degenerate box`() {
        listOf(Float.NaN, 0f, -2f, Float.POSITIVE_INFINITY).forEach { ratio ->
            val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, ratio)
            assertEquals(3f / 2f, EntryImageSizing.sanitizeAspectRatio(ratio), epsilon)
            assertEquals(viewportWidth, box.width, epsilon)
            assertEquals(viewportWidth / (3f / 2f), box.height, epsilon)
        }
    }

    @Test
    fun `absurd aspect ratios are sanitized`() {
        assertEquals(20f, EntryImageSizing.sanitizeAspectRatio(100f), epsilon)
        assertEquals(0.1f, EntryImageSizing.sanitizeAspectRatio(0.001f), epsilon)
    }

    @Test
    fun `zoom is clamped between 1x and 4x`() {
        assertEquals(1f, EntryImageSizing.clampScale(0.5f), epsilon)
        assertEquals(1f, EntryImageSizing.clampScale(1f), epsilon)
        assertEquals(2.5f, EntryImageSizing.clampScale(2.5f), epsilon)
        assertEquals(4f, EntryImageSizing.clampScale(10f), epsilon)
        assertEquals(1f, EntryImageSizing.clampScale(Float.NaN), epsilon)
    }

    @Test
    fun `double tap zooms to 25x and resets back to 1x`() {
        assertEquals(2.5f, EntryImageSizing.doubleTapScale(1f), epsilon)
        assertEquals(1f, EntryImageSizing.doubleTapScale(2.5f), epsilon)
        assertEquals(1f, EntryImageSizing.doubleTapScale(4f), epsilon)
        assertEquals(2.5f, EntryImageSizing.doubleTapScale(1f + 0.001f), epsilon)
    }

    @Test
    fun `a non-zoomed picture cannot be panned`() {
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, 2f / 3f)
        val content = EntryImageSizing.fitContent(box.width, box.height, 2f / 3f)
        val bounds = EntryImageSizing.panBounds(box.width, box.height, content, 1f)
        val pan = EntryImageSizing.clampPan(120f, -90f, bounds)

        assertEquals(0f, bounds.maxX, epsilon)
        assertEquals(0f, bounds.maxY, epsilon)
        assertEquals(0f, pan.x, epsilon)
        assertEquals(0f, pan.y, epsilon)
    }

    @Test
    fun `pan bounds grow with the zoom and the pan is clamped inside them`() {
        val box = EntryImageSizing.imageBox(viewportWidth, viewportHeight, 2f / 3f)
        val content = EntryImageSizing.fitContent(box.width, box.height, 2f / 3f)
        val bounds = EntryImageSizing.panBounds(box.width, box.height, content, 2f)

        // 2x content: 348.85 x 523.28 -> 697.7 x 1046.56.
        assertEquals((content.width * 2f - box.width) / 2f, bounds.maxX, epsilon)
        assertEquals((content.height * 2f - box.height) / 2f, bounds.maxY, epsilon)
        assertTrue(bounds.maxX > 0f)
        assertTrue(bounds.maxY > 0f)

        val clamped = EntryImageSizing.clampPan(10_000f, -10_000f, bounds)
        assertEquals(bounds.maxX, clamped.x, epsilon)
        assertEquals(-bounds.maxY, clamped.y, epsilon)
    }

    @Test
    fun `fit content of a zero-sized box stays empty instead of dividing by zero`() {
        assertEquals(EntryContentSize(0f, 0f), EntryImageSizing.fitContent(0f, 0f, 1.5f))
        assertEquals(EntryImageBox(0f, 0f), EntryImageSizing.imageBox(0f, viewportHeight, 1.5f))
    }
}
