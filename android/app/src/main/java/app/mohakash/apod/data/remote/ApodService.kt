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

    /** Downloads a raw document; used to keep the cached copy byte identical. */
    suspend fun getString(path: String): String
}
