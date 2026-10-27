package app.mohakash.apod.carousel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The next time the carousel worker has been asked to run. */
@Serializable
data class CarouselNextRun(
    val nextAt: Long,
    /** The full rotation interval when known; zero while the collection is still being prepared. */
    val intervalMillis: Long = 0L,
)

/** Small synchronous store so the History tab can show a countdown before the first pick. */
class CarouselNextRunStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    @Synchronized
    fun read(): CarouselNextRun? {
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            json.decodeFromString(CarouselNextRun.serializer(), file.readText())
        }.getOrNull()
    }

    @Synchronized
    fun write(run: CarouselNextRun) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(CarouselNextRun.serializer(), run))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }
}
