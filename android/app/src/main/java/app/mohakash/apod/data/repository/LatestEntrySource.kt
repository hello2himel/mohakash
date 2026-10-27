package app.mohakash.apod.data.repository

import app.mohakash.apod.domain.model.ApodEntry

/**
 * The source of the latest entry.
 *
 * The interface keeps the synchronisation logic independent of the caching layer.
 */
interface LatestEntrySource {

    /** Fetches the latest entry and caches it. */
    suspend fun fetchLatest(forceNetwork: Boolean = false): ApodEntry

    /** The last cached latest entry, if any. Implementations may return null. */
    suspend fun cachedLatest(): ApodEntry? = null
}
