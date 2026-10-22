package app.mohakash.apod.data.remote

import app.mohakash.apod.data.remote.dto.ApodRecordDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexDto
import app.mohakash.apod.data.remote.dto.ArchiveIndexEntryDto
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Normalisation of the public JSON API.
 */
class ApiMapperTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `maps a complete record`() {
        val dto = ApodRecordDto(
            date = "2026-10-22",
            title = "The First Green Star",
            explanation = "First paragraph.\n\nSecond paragraph.",
            mediaType = "image",
            imageUrl = "https://example.org/photo.jpg?w=1600",
            hdImageUrl = "https://example.org/photo.jpg?w=2400",
            thumbnailUrl = "https://example.org/photo.jpg?w=480",
            apodUrl = "https://science.nasa.gov/image-article/apod-2026-october-22/",
            copyright = "Mark A. Garlick",
            credit = "Mark A. Garlick",
            altText = "A greenish star",
            source = "NASA APOD",
            sourceId = "1497603",
            sourceMediaType = "image",
            imageStatus = "ok",
            updatedAt = "2026-10-22T12:17:44Z",
            lastSuccessfulSync = "2026-10-22T12:19:53Z",
            latestAvailableDate = "2026-10-22",
            recordCount = 94,
            sourceStatus = "ok",
        )

        val entry = ApiMapper.toDomain(dto)

        assertEquals("2026-10-22", entry.date)
        assertEquals("The First Green Star", entry.title)
        assertEquals(MediaType.IMAGE, entry.mediaType)
        assertEquals(ImageStatus.OK, entry.imageStatus)
        assertEquals("Mark A. Garlick", entry.displayCredit)
        assertEquals(listOf("First paragraph.", "Second paragraph."), entry.paragraphs)
        assertEquals(94, entry.recordCount)
        assertTrue(entry.supportsWallpaper)
    }

    @Test
    fun `missing fields never break the mapping`() {
        val entry = ApiMapper.toDomain(ApodRecordDto(date = "2026-01-01"))

        assertEquals("2026-01-01", entry.date)
        assertEquals("Untitled", entry.title)
        assertEquals("", entry.explanation)
        assertNull(entry.imageUrl)
        assertNull(entry.hdImageUrl)
        assertNull(entry.thumbnailUrl)
        assertNull(entry.apodUrl)
        assertNull(entry.displayCredit)
        assertEquals(MediaType.OTHER, entry.mediaType)
        assertEquals(ImageStatus.MISSING, entry.imageStatus)
        assertFalse(entry.supportsWallpaper)
    }

    @Test
    fun `blank strings are treated as absent`() {
        val entry = ApiMapper.toDomain(
            ApodRecordDto(
                date = " 2026-01-01 ",
                title = "   ",
                explanation = "",
                imageUrl = "  ",
                copyright = "",
            ),
        )

        assertEquals("2026-01-01", entry.date)
        assertEquals("Untitled", entry.title)
        assertNull(entry.imageUrl)
        assertNull(entry.displayCredit)
    }

    @Test
    fun `media type variants are mapped`() {
        assertEquals(MediaType.IMAGE, MediaType.from("image"))
        assertEquals(MediaType.VIDEO, MediaType.from("VIDEO"))
        assertEquals(MediaType.OTHER, MediaType.from("other"))
        assertEquals(MediaType.OTHER, MediaType.from("iframe"))
        assertEquals(MediaType.OTHER, MediaType.from(null))
        assertEquals(MediaType.OTHER, MediaType.from("something-else"))
    }

    @Test
    fun `image status variants are mapped`() {
        assertEquals(ImageStatus.OK, ImageStatus.from("ok"))
        assertEquals(ImageStatus.PLACEHOLDER, ImageStatus.from("placeholder"))
        assertEquals(ImageStatus.MISSING, ImageStatus.from("missing"))
        assertEquals(ImageStatus.OK, ImageStatus.from(null))
    }

    @Test
    fun `a placeholder picture is never usable`() {
        val entry = ApiMapper.toDomain(
            ApodRecordDto(
                date = "2026-01-01",
                mediaType = "image",
                imageStatus = "placeholder",
            ),
        )

        assertEquals(ImageStatus.PLACEHOLDER, entry.imageStatus)
        assertFalse(entry.imageStatus.isUsable)
        assertFalse(entry.supportsWallpaper)
    }

    @Test
    fun `a missing image status is derived from the urls`() {
        val withImage = ApiMapper.toDomain(
            ApodRecordDto(date = "2026-01-01", imageUrl = "https://example.org/a.jpg"),
        )
        val withoutImage = ApiMapper.toDomain(ApodRecordDto(date = "2026-01-01"))

        assertEquals(ImageStatus.OK, withImage.imageStatus)
        assertEquals(ImageStatus.MISSING, withoutImage.imageStatus)
    }

    @Test
    fun `the index is mapped and entries without a date are dropped`() {
        val dto = ArchiveIndexDto(
            count = 2,
            entries = listOf(
                ArchiveIndexEntryDto(
                    date = "2026-10-22",
                    title = "A title",
                    mediaType = "image",
                    thumbnailUrl = "https://example.org/a.jpg?w=480",
                    imageUrl = "https://example.org/a.jpg?w=1600",
                    apodUrl = "https://example.org/page",
                    copyright = "Someone",
                    detailPath = "https://example.org/archive/2026.json",
                ),
                ArchiveIndexEntryDto(date = null, title = "Broken"),
            ),
        )

        val entries = ApiMapper.toDomain(dto)

        assertEquals(1, entries.size)
        assertEquals("2026-10-22", entries.first().date)
        assertEquals(2026, entries.first().year)
        assertEquals("Someone", entries.first().displayCredit)
    }

    @Test
    fun `the real api payload is understood`() {
        val payload = """
            {
              "schema_version": "1.0.0",
              "date": "2026-10-22",
              "title": "The First Green Star, WD J1330+6435",
              "explanation": "Star light, star bright.\n\nTomorrow's picture: eye in the sky",
              "media_type": "image",
              "image_url": "https://assets.science.nasa.gov/a.jpg?w=1600",
              "hd_image_url": "https://assets.science.nasa.gov/a.jpg?w=2400",
              "thumbnail_url": "https://assets.science.nasa.gov/a.jpg?w=480",
              "apod_url": "https://science.nasa.gov/image-article/apod-2026-october-22/",
              "copyright": "Mark A. Garlick/University of Warwick",
              "credit": "Mark A. Garlick/University of Warwick",
              "alt_text": "A star shines with a greenish hue.",
              "source": "NASA APOD",
              "source_id": "1497603",
              "source_media_type": "image",
              "image_status": "ok",
              "updated_at": "2026-10-22T12:17:44Z",
              "last_successful_sync": "2026-10-22T12:19:53Z",
              "latest_available_date": "2026-10-22",
              "record_count": 11444,
              "source_status": "ok"
            }
        """.trimIndent()

        val entry = ApiMapper.toDomain(json.decodeFromString<ApodRecordDto>(payload))

        assertEquals("2026-10-22", entry.date)
        assertEquals(MediaType.IMAGE, entry.mediaType)
        assertEquals(ImageStatus.OK, entry.imageStatus)
        assertEquals("Mark A. Garlick/University of Warwick", entry.displayCredit)
        assertEquals(2, entry.paragraphs.size)
        assertEquals(11444, entry.recordCount)
    }
}
