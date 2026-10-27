package app.mohakash.apod.carousel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Atomic-enough JSON store for the validated collection of the current month/day. */
class CarouselCollectionStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val mutex = Mutex()

    suspend fun read(): CarouselCollection? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!file.isFile || file.length() == 0L) return@withLock null
                json.decodeFromString<CarouselCollection>(file.readText())
            }.getOrNull()
        }
    }

    suspend fun write(collection: CarouselCollection) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.parentFile?.mkdirs()
            val temp = File.createTempFile("carousel-collection-", ".tmp", file.parentFile)
            temp.writeText(json.encodeToString(CarouselCollection.serializer(), collection))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }
}

/** Cross-run last selection, used to avoid showing the same image twice in a row. */
class CarouselSelectionStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    private val mutex = Mutex()

    suspend fun read(): CarouselSelection? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                if (!file.isFile || file.length() == 0L) return@withLock null
                json.decodeFromString<CarouselSelection>(file.readText())
            }.getOrNull()
        }
    }

    suspend fun write(selection: CarouselSelection) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.parentFile?.mkdirs()
            val temp = File.createTempFile("carousel-selection-", ".tmp", file.parentFile)
            temp.writeText(json.encodeToString(CarouselSelection.serializer(), selection))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }
}
