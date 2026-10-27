package app.mohakash.apod

import android.content.Context
import app.mohakash.apod.core.NetworkMonitor
import app.mohakash.apod.data.local.db.MohakashDatabase
import app.mohakash.apod.data.local.prefs.PreferencesSettingsStore
import app.mohakash.apod.data.local.prefs.SettingsStore
import app.mohakash.apod.data.remote.ApodApi
import app.mohakash.apod.data.remote.BaseUrlResolver
import app.mohakash.apod.data.repository.ApodRepository
import app.mohakash.apod.data.repository.ArchiveIndexRepository
import app.mohakash.apod.data.repository.FileArchiveIndexRepository
import app.mohakash.apod.data.repository.FavouritesRepository
import app.mohakash.apod.data.repository.ImageRepository
import app.mohakash.apod.data.storage.CacheManager
import app.mohakash.apod.data.storage.GallerySaver
import app.mohakash.apod.data.storage.ImageDownloader
import app.mohakash.apod.data.storage.NotificationHelper
import app.mohakash.apod.data.storage.ShareHelper
import app.mohakash.apod.data.storage.WallpaperStorage
import app.mohakash.apod.donation.DonationStateStore
import app.mohakash.apod.donation.DonationTracker
import app.mohakash.apod.carousel.CarouselCollectionStore
import app.mohakash.apod.carousel.CarouselController
import app.mohakash.apod.carousel.CarouselDiscovery
import app.mohakash.apod.carousel.CarouselSelectionStore
import app.mohakash.apod.carousel.HttpCarouselImageValidator
import app.mohakash.apod.work.ApodSyncEngine
import app.mohakash.apod.work.SyncController
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import java.io.File

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

    /**
     * The archive index, cached in `cacheDir/feed/archive-index.json`.
     *
     * One app-scoped instance is shared by the feed, its search and its filter sheet: parsing the
     * 5 MiB payload once and keeping the result in memory is what makes the combined screen cheap.
     */
    val archiveIndexRepository: ArchiveIndexRepository by lazy {
        FileArchiveIndexRepository(
            api = api,
            json = json,
            cacheFile = java.io.File(appContext.cacheDir, "feed/archive-index.json"),
        )
    }

    val imageDownloader: ImageDownloader by lazy { ImageDownloader(httpClient) }

    val wallpaperStorage: WallpaperStorage by lazy { WallpaperStorage(appContext.filesDir, json) }

    val gallerySaver: GallerySaver by lazy { GallerySaver(appContext) }

    val cacheManager: CacheManager by lazy { CacheManager(appContext, httpClient) }

    val syncController: SyncController by lazy { SyncController(appContext) }

    val shareHelper: ShareHelper by lazy { ShareHelper(appContext) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper.get(appContext) }

    val donationStateStore: DonationStateStore by lazy {
        DonationStateStore(File(appContext.filesDir, "support/donation-state.json"), json)
    }

    val donationTracker: DonationTracker by lazy { DonationTracker(donationStateStore) }

    val imageRepository: ImageRepository by lazy {
        ImageRepository(appContext, imageDownloader, gallerySaver)
    }

    val carouselCollectionStore: CarouselCollectionStore by lazy {
        CarouselCollectionStore(File(appContext.filesDir, "carousel/collection.json"))
    }

    val carouselSelectionStore: CarouselSelectionStore by lazy {
        CarouselSelectionStore(File(appContext.filesDir, "carousel/selection.json"))
    }

    val carouselController: CarouselController by lazy {
        CarouselController(
            context = appContext,
            settingsStore = settingsStore,
            discovery = CarouselDiscovery(
                archiveIndexRepository = archiveIndexRepository,
                validator = HttpCarouselImageValidator(httpClient),
                collectionStore = carouselCollectionStore,
                latestSource = apodRepository,
            ),
            selectionStore = carouselSelectionStore,
            syncEngine = syncEngine,
            wallpaperStorage = wallpaperStorage,
        )
    }

    val syncEngine: ApodSyncEngine by lazy {
        ApodSyncEngine(
            apodRepository = apodRepository,
            settingsStore = settingsStore,
            wallpaperStorage = wallpaperStorage,
            imageDownloader = imageDownloader,
            notifyWallpaperChanged = { entry -> notificationHelper.notifyWallpaperChanged(entry) },
        )
    }

    private companion object {
        const val HTTP_CACHE_BYTES = 8L * 1024 * 1024
    }
}
