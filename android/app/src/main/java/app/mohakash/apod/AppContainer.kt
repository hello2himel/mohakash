package app.mohakash.apod

import android.content.Context
import app.mohakash.apod.core.NetworkMonitor
import app.mohakash.apod.data.local.db.MohakashDatabase
import app.mohakash.apod.data.local.prefs.PreferencesSettingsStore
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.remote.ApodApi
import app.mohakash.apod.data.remote.BaseUrlResolver
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.FavouritesRepository
import app.mohakash.apod.data.repository.ImageRepository
import app.mohakash.apod.data.storage.CacheManager
import app.mohakash.apod.data.storage.GallerySaver
import app.mohakash.apod.data.storage.ImageDownloader
import app.mohakash.apod.data.storage.ShareHelper
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.notification.WallpaperNotifier
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.SyncController
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * The dependency container.
 *
 * Everything is created lazily so starting the app stays cheap and the wallpaper service can
 * reuse the very same instances as the UI.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    val database: MohakashDatabase by lazy { MohakashDatabase.get(appContext) }

    val settingsStore: SettingsStore by lazy { PreferencesSettingsStore(appContext) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(java.io.File(appContext.cacheDir, "http"), HTTP_CACHE_BYTES))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                },
            )
            .build()
    }

    /**
     * The candidate base URLs come from `config/site.json` and are injected at build time.
     */
    val baseUrlResolver: BaseUrlResolver by lazy {
        BaseUrlResolver(
            candidates = BuildConfig.API_BASE_URL_FALLBACKS.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            settingsStore = settingsStore,
        )
    }

    val api: ApodApi by lazy { ApodApi(baseUrlResolver, httpClient, json) }

    val apodRepository: ApodRepository by lazy {
        ApodRepository(
            api = api,
            cacheDocumentDao = database.cacheDocumentDao(),
            cachedEntryDao = database.cachedEntryDao(),
            settingsStore = settingsStore,
            json = json,
        )
    }

    val favouritesRepository: FavouritesRepository by lazy {
        FavouritesRepository(database.favouriteDao())
    }

    val imageDownloader: ImageDownloader by lazy { ImageDownloader(httpClient) }

    val wallpaperStorage: WallpaperStorage by lazy { WallpaperStorage(appContext.filesDir, json) }

    val gallerySaver: GallerySaver by lazy { GallerySaver(appContext) }

    val cacheManager: CacheManager by lazy { CacheManager(appContext) }

    val syncController: SyncController by lazy { SyncController(appContext) }

    val shareHelper: ShareHelper by lazy { ShareHelper(appContext) }

    val imageRepository: ImageRepository by lazy {
        ImageRepository(appContext, imageDownloader, gallerySaver)
    }

    val syncEngine: ApodSyncEngine by lazy {
        ApodSyncEngine(
            apodRepository = apodRepository,
            settingsStore = settingsStore,
            wallpaperStorage = wallpaperStorage,
            imageDownloader = imageDownloader,
            notifier = WallpaperUpdateNotifier { entry -> wallpaperNotifier.notifyNewWallpaper(entry) },
        )
    }

    val wallpaperNotifier: WallpaperNotifier by lazy { WallpaperNotifier(appContext) }

    private companion object {
        const val HTTP_CACHE_BYTES = 8L * 1024 * 1024
    }
}
