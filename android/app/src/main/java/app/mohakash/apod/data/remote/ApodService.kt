package app.mohakash.apod.data.remote

import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry

/**
 * The public JSON API of the project.
 *
 * The interface exists so the repository layer can be tested without a network.
 */
interface ApodService {

    /** The latest entry (`latest.json`). */
    suspend fun latest(): ApodEntry

    /** The lightweight archive index (`archive-index.json`). */
    suspend fun archiveIndex(): List<ArchiveIndexEntry>

    /**
     * Downloads a raw document; used to keep the cached copy byte identical.
     *
     * [maxBytes] bounds the *decompressed* response size. Implementations must reject a larger
     * document with an [java.io.IOException] before buffering it whole, so a misbehaving or
     * compromised endpoint cannot exhaust the heap. The parameter is defaulted so existing call
     * sites remain source-compatible.
     */
    suspend fun getString(path: String, maxBytes: Long = DEFAULT_MAX_RESPONSE_BYTES): String

    companion object {
        /** 16 MiB: roughly three times the largest document the app serves today. */
        const val DEFAULT_MAX_RESPONSE_BYTES: Long = 16L * 1024 * 1024
    }
}
