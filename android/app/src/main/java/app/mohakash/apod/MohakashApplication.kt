package app.mohakash.apod

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * The application object.
 *
 * It owns the dependency container and configures the shared Coil image loader with a bounded
 * disk cache (64 MB) and an in-memory cache.
 */
class MohakashApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(IMAGE_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()

    companion object {
        /** 64 MB of pictures on disk. */
        const val IMAGE_CACHE_BYTES = 64L * 1024 * 1024
    }
}
