package app.mohakash.apod.domain.wallpaper

import app.mohakash.apod.domain.model.WallpaperScaling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry of the *Fill* and *Fit* wallpaper modes.
 */
class WallpaperGeometryTest {

    @Test
    fun `fill crops the sides of a wide picture`() {
        // A 2000 x 1000 picture on a 1080 x 2400 screen.
        val source = WallpaperGeometry.computeSourceRect(2000, 1000, 1080, 2400, WallpaperScaling.FILL)

        assertEquals(1000, source.height)
        assertEquals(450, source.width)
        assertEquals(775, source.left)
        assertEquals(1225, source.right)
        assertEquals(0, source.top)
        assertEquals(1000, source.bottom)
    }

    @Test
    fun `fill crops the top and the bottom of a tall picture`() {
        // A 1000 x 3000 picture on a 1080 x 2400 screen.
        val source = WallpaperGeometry.computeSourceRect(1000, 3000, 1080, 2400, WallpaperScaling.FILL)

        assertEquals(1000, source.width)
        assertEquals(2222, source.height)
        assertEquals(0, source.left)
        assertEquals(1000, source.right)
        assertEquals(389, source.top)
        assertEquals(2611, source.bottom)
    }

    @Test
    fun `fill keeps the whole screen as the destination`() {
        val destination = WallpaperGeometry.computeDestinationRect(2000, 1000, 1080, 2400, WallpaperScaling.FILL)

        assertEquals(PixelRect(0, 0, 1080, 2400), destination)
    }

    @Test
    fun `fit keeps the whole picture`() {
        val source = WallpaperGeometry.computeSourceRect(2000, 1000, 1080, 2400, WallpaperScaling.FIT)

        assertEquals(PixelRect(0, 0, 2000, 1000), source)
    }

    @Test
    fun `fit letterboxes the picture in the middle of the screen`() {
        val destination = WallpaperGeometry.computeDestinationRect(2000, 1000, 1080, 2400, WallpaperScaling.FIT)

        // The picture keeps its 2:1 ratio: 1080 wide and 540 high, centred vertically.
        assertEquals(1080, destination.width)
        assertEquals(540, destination.height)
        assertEquals(0, destination.left)
        assertEquals(1080, destination.right)
        assertEquals(930, destination.top)
        assertEquals(1470, destination.bottom)
    }

    @Test
    fun `fit letterboxes a square picture`() {
        val destination = WallpaperGeometry.computeDestinationRect(1000, 1000, 1000, 2000, WallpaperScaling.FIT)

        assertEquals(1000, destination.width)
        assertEquals(1000, destination.height)
        assertEquals(0, destination.left)
        assertEquals(500, destination.top)
        assertEquals(1500, destination.bottom)
    }

    @Test
    fun `degenerate sizes produce an empty rectangle`() {
        assertTrue(WallpaperGeometry.computeSourceRect(0, 100, 100, 100, WallpaperScaling.FILL).isEmpty)
        assertTrue(WallpaperGeometry.computeSourceRect(100, 100, 0, 100, WallpaperScaling.FILL).isEmpty)
        assertTrue(WallpaperGeometry.computeDestinationRect(-1, 100, 100, 100, WallpaperScaling.FIT).isEmpty)
    }

    @Test
    fun `the sample size keeps the picture large enough for the screen`() {
        // A picture that is smaller than the screen is never sampled.
        assertEquals(1, WallpaperGeometry.computeSampleSize(1600, 1200, 1080, 2400))
        assertEquals(1, WallpaperGeometry.computeSampleSize(320, 240, 1080, 2400))

        // A huge picture is sampled down, and the sample size is always a power of two.
        val sample = WallpaperGeometry.computeSampleSize(12000, 9000, 1080, 2400)
        assertTrue(sample > 1)
        assertEquals(0, sample and (sample - 1))
    }

    @Test
    fun `the sample size never exceeds the maximum dimension`() {
        val sample = WallpaperGeometry.computeSampleSize(12000, 9000, 1080, 2400, maxDimension = 2880)
        assertTrue(sample >= 1)
        assertTrue(maxOf(12000 / sample, 9000 / sample) <= 2 * 2880)
    }

    @Test
    fun `the fill crop has the aspect ratio of the screen`() {
        val source = WallpaperGeometry.computeSourceRect(3000, 2000, 1080, 2400, WallpaperScaling.FILL)
        val sourceAspect = source.width.toDouble() / source.height.toDouble()
        val destinationAspect = 1080.0 / 2400.0

        assertEquals(destinationAspect, sourceAspect, 0.01)
    }

    @Test
    fun `the fit destination has the aspect ratio of the picture`() {
        val destination = WallpaperGeometry.computeDestinationRect(3000, 2000, 1080, 2400, WallpaperScaling.FIT)
        val destinationAspect = destination.width.toDouble() / destination.height.toDouble()
        val sourceAspect = 3000.0 / 2000.0

        assertEquals(sourceAspect, destinationAspect, 0.01)
        assertFalse(destination.isEmpty)
    }
}
