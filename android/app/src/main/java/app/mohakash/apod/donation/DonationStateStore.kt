package app.mohakash.apod.donation

import kotlinx.serialization.json.Json
import java.io.File

/** Atomic JSON persistence for [DonationState]. */
class DonationStateStore(
    private val file: File,
    private val json: Json,
) {
    fun read(): DonationState {
        if (!file.isFile) return DonationState()
        return runCatching {
            json.decodeFromString(DonationState.serializer(), file.readText())
        }.getOrDefault(DonationState())
    }

    fun write(state: DonationState) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(DonationState.serializer(), state))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        }
    }
}
