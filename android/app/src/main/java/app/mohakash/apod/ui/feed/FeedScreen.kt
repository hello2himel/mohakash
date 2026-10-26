package app.mohakash.apod.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.rememberLegacyStoragePermission
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.navigation.openWallpaperPicker
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.wallpaper.WallpaperSheet
import app.mohakash.apod.wallpaper.WallpaperStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The combined feed: one full-screen APOD per page, newest first.
 *
 * Swiping up moves to an older picture, swiping down to a newer one; the list never wraps and the
 * last page is an explicit end-of-archive card. The route string stays `today` so the start
 * destination, the deep links and the Saved tab need no migration.
 */
@Composable
fun FeedScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
    onOpenSaved: () -> Unit = {},
    randomRequest: Int = 0,
    onRandomRequestConsumed: () -> Unit = {},
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val viewModel = containerViewModel { container ->
        FeedViewModel(
            apodRepository = container.apodRepository,
            archiveIndexRepository = container.archiveIndexRepository,
            favouritesRepository = container.favouritesRepository,
            imageRepository = container.imageRepository,
            settingsStore = container.settingsStore,
            shareHelper = container.shareHelper,
            wallpaperStorage = container.wallpaperStorage,
            syncEngine = container.syncEngine,
            syncController = container.syncController,
            isWallpaperActive = { WallpaperStatus.isActive(appContext) },
            isUnmetered = { container.networkMonitor.isUnmetered() },
            isConnected = { container.networkMonitor.isConnected() },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    var systemWallpaperActive by remember { mutableStateOf(WallpaperStatus.isActive(appContext)) }
    var showSearch by remember { mutableStateOf(false) }
    var pendingSaveDate by remember { mutableStateOf<String?>(null) }
    // A tap on the picture hides every overlay and the bottom bar; a second tap brings them back.
    var chromeVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(state.currentPageKey) {
        // A new page always starts readable.
        chromeVisible = true
    }
    LaunchedEffect(chromeVisible) {
        onFullscreenChange(!chromeVisible)
    }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }
    // Back restores the chrome instead of leaving the app while the feed is in fullscreen.
    BackHandler(enabled = !chromeVisible) { chromeVisible = true }

    // The picker is a separate activity, so the label has to be re-checked when the feed resumes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemWallpaperActive = WallpaperStatus.isActive(appContext)
        viewModel.onResume()
    }

    val saveImage = rememberLegacyStoragePermission(
        onGranted = {
            pendingSaveDate?.let(viewModel::saveImage)
            pendingSaveDate = null
        },
        onDenied = {
            pendingSaveDate = null
            scope.launch { snackbarHostState.showSnackbar(MESSAGE_PERMISSION_DENIED) }
        },
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                FeedEvent.OpenWallpaperPicker -> openWallpaperPicker(context)
            }
        }
    }

    // A new reader gets one quiet, non-blocking cue that the feed is vertical. It is also
    // dismissed as soon as the pager starts moving; the preference makes it a once-only event.
    val canShowSwipeHint = state.showSwipeHint &&
        state.pages.count { !it.isEnd } > 1 &&
        state.currentPageKey != FeedPage.End.key &&
        !state.filters.isActive

    // Pause the one-time hint's timer while the activity is not resumed. Without this, opening
    // the system wallpaper picker from onboarding consumes the cue behind the picker.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(canShowSwipeHint, lifecycleOwner) {
        if (!canShowSwipeHint) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(SWIPE_HINT_VISIBLE_MILLIS)
            viewModel.dismissSwipeHint()
        }
    }

    LaunchedEffect(randomRequest) {
        if (randomRequest > 0) {
            onRandomRequestConsumed()
            viewModel.pickRandom()
        }
    }

    val actions = remember(viewModel, onOpenEntry, onOpenSettings, onOpenLink) {
        FeedPageActions(
            onOpenEntry = onOpenEntry,
            onOpenSettings = onOpenSettings,
            onOpenSearch = {
                viewModel.openSearch()
                showSearch = true
            },
            onToggleFavourite = viewModel::toggleFavourite,
            onSaveImage = { date ->
                pendingSaveDate = date
                saveImage()
            },
            onShare = viewModel::share,
            onOpenWallpaper = viewModel::openWallpaperSheet,
            onRetryHydration = viewModel::retryHydration,
            onRefresh = viewModel::refreshLatest,
            onOpenLink = onOpenLink,
            onClearFilters = viewModel::clearFilters,
        )
    }

    FeedContent(
        state = state,
        actions = actions,
        wallpaperActiveFor = { date ->
            systemWallpaperActive && state.wallpaperMeta?.date == date
        },
        onPageSettled = viewModel::onPageSettled,
        onConsumeJump = viewModel::consumeJumpRequest,
        onBackToLatest = {
            val firstDate = state.pages.firstNotNullOfOrNull { it.date }
            if (firstDate != null) viewModel.jumpToDate(firstDate) else viewModel.clearFilters()
        },
        onOpenSaved = onOpenSaved,
        onRetryAll = viewModel::retry,
        showSwipeHint = canShowSwipeHint,
        onDismissSwipeHint = viewModel::dismissSwipeHint,
        chromeVisible = chromeVisible,
        onToggleChrome = { chromeVisible = !chromeVisible },
        modifier = modifier,
    )

    if (showSearch) {
        FeedSearchSheet(
            state = state.search,
            availableYears = state.availableYears,
            onDismiss = { showSearch = false },
            onQueryChange = viewModel::setSearchQuery,
            onFiltersChange = viewModel::setSearchFilters,
            onSelectResult = { date ->
                showSearch = false
                viewModel.jumpToDate(date)
            },
            onRetryIndex = viewModel::ensureIndex,
        )
    }

    val wallpaperDate = state.wallpaperSheetDate
    if (wallpaperDate != null) {
        val entry = state.pages.firstOrNull { it.date == wallpaperDate }?.entry
        WallpaperSheet(
            scaling = state.scaling,
            updateDaily = state.updateDaily,
            preview = feedWallpaperPreview(entry, state.wallpaperMeta, state.wallpaperPreviewPath),
            caption = feedWallpaperCaption(entry, state.wallpaperMeta),
            onDismiss = viewModel::dismissWallpaperSheet,
            onScalingChange = viewModel::setScaling,
            onUpdateDailyChange = viewModel::setUpdateDaily,
            onPreviewAndSet = viewModel::previewAndSetWallpaper,
            isWallpaperActive = systemWallpaperActive,
        )
    }

    if (state.isPickingRandom) {
        FeedRandomPickOverlay(onCancel = viewModel::cancelRandomPick)
    }
}

/**
 * The stateless feed, driven by [state] and [actions].
 *
 * The pager is the whole screen; the top chrome and the bottom overlay live inside each page so
 * the image can draw edge to edge behind them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedContent(
    state: FeedUiState,
    actions: FeedPageActions,
    wallpaperActiveFor: (String) -> Boolean,
    onPageSettled: (Int) -> Unit,
    onConsumeJump: (Long) -> Unit,
    onBackToLatest: () -> Unit,
    onOpenSaved: () -> Unit,
    onRetryAll: () -> Unit,
    showSwipeHint: Boolean,
    onDismissSwipeHint: () -> Unit,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        val hintBottomPadding = (maxHeight * 0.38f).coerceIn(140.dp, 320.dp)
        when {
            state.pages.isEmpty() && state.hasFatalError -> FeedFatalError(
                onRetry = onRetryAll,
                onOpenSaved = onOpenSaved,
            )

            state.pages.isEmpty() -> FeedSkeleton()

            else -> FeedPager(
                state = state,
                actions = actions,
                wallpaperActiveFor = wallpaperActiveFor,
                onPageSettled = onPageSettled,
                onConsumeJump = onConsumeJump,
                onBackToLatest = onBackToLatest,
                onOpenSaved = onOpenSaved,
                onRetryAll = onRetryAll,
                showSwipeHint = showSwipeHint,
                onDismissSwipeHint = onDismissSwipeHint,
                chromeVisible = chromeVisible,
                onToggleChrome = onToggleChrome,
            )
        }

        AnimatedVisibility(
            visible = showSwipeHint,
            enter = fadeIn(tween(220)) +
                slideInVertically(tween(220)) { height -> height / 2 },
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = hintBottomPadding)
                .padding(horizontal = 24.dp),
        ) {
            FeedSwipeHint()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPager(
    state: FeedUiState,
    actions: FeedPageActions,
    wallpaperActiveFor: (String) -> Boolean,
    onPageSettled: (Int) -> Unit,
    onConsumeJump: (Long) -> Unit,
    onBackToLatest: () -> Unit,
    onOpenSaved: () -> Unit,
    onRetryAll: () -> Unit,
    showSwipeHint: Boolean,
    onDismissSwipeHint: () -> Unit,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 0) { state.pages.size }
    val pullState = rememberPullToRefreshState()
    val pages = state.pages
    val currentPageKey = state.currentPageKey
    val listVersion = state.listVersion

    // Keep the current date under the thumb when the list is rebuilt (index arrives, filters
    // apply, a new year is appended). A single instant scroll — never an animation across
    // thousands of pages.
    LaunchedEffect(listVersion, currentPageKey, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        val target = if (currentPageKey == FeedPage.End.key) {
            pages.lastIndex
        } else {
            FeedPaging.pageIndexForDate(pages, currentPageKey)
        }
        if (target in pages.indices && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }

    // One-shot requests from the ViewModel (search result, jump-to-date, filter apply).
    val jumpRequest = state.jumpRequest
    LaunchedEffect(jumpRequest?.token) {
        val request = jumpRequest ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val target = if (request.date == FeedPage.End.key) {
            pages.lastIndex
        } else {
            FeedPaging.pageIndexForDate(pages, request.date)
        }
        if (target in pages.indices) {
            if (request.animate) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
        onConsumeJump(request.token)
    }

    LaunchedEffect(pagerState) {
        // Reading the page count as well matters: when the first pages arrive the settled page
        // stays 0, so a flow over `settledPage` alone would never emit and page 0 would not be
        // hydrated or prefetched.
        snapshotFlow { pagerState.settledPage to pagerState.pageCount }
            .distinctUntilChanged()
            .collect { (index, count) -> if (count > 0) onPageSettled(index) }
    }

    // The cue has done its job the moment the reader starts dragging the feed. Watching the
    // drag interaction (rather than isScrollInProgress) keeps programmatic page reconciliation
    // from dismissing the cue before the reader has seen it.
    val isDragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(showSwipeHint, isDragging) {
        if (showSwipeHint && isDragging) onDismissSwipeHint()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            FeedPullRefreshIndicator(state = pullState, isRefreshing = state.isRefreshing)
        },
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { index -> pages.getOrNull(index)?.page?.key ?: "feed-$index" },
        ) { index ->
            val page = pages.getOrNull(index) ?: return@VerticalPager
            if (page.isEnd) {
                FeedEndCard(
                    filters = state.filters,
                    isIndexComplete = state.isIndexComplete,
                    isOffline = state.isOffline,
                    onBackToLatest = onBackToLatest,
                    onOpenSaved = onOpenSaved,
                    onClearFilters = actions.onClearFilters,
                    onRetry = onRetryAll,
                )
            } else {
                FeedPageContent(
                    page = page,
                    isFavourite = page.date in state.favourites,
                    wallpaperActive = page.date?.let(wallpaperActiveFor) == true,
                    scaling = state.scaling,
                    isOffline = state.isOffline,
                    filters = state.filters,
                    actions = actions,
                    chromeVisible = chromeVisible,
                    onToggleChrome = onToggleChrome,
                )
            }
        }
    }
}

/** Shown when the legacy storage permission is refused. */
private const val MESSAGE_PERMISSION_DENIED = "Storage permission is needed to save the image."

/** How long the one-time swipe cue stays on screen when the reader does nothing. */
private const val SWIPE_HINT_VISIBLE_MILLIS = 6_000L
