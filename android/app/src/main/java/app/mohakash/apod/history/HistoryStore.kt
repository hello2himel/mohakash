package app.mohakash.apod.history

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Atomic JSON persistence for the small history list. */
class HistoryStore(
    private val file: File,
    private val json: Json,
) {
    fun read(): List<HistoryItem> {
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(HistoryItem.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun write(items: List<HistoryItem>) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(ListSerializer(HistoryItem.serializer()), items))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }
}
