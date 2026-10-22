package app.mohakash.apod.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.FullBleedImageFrame
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.components.rememberLegacyStoragePermission
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.navigation.openWallpaperPicker
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import app.mohakash.apod.ui.wallpaper.WallpaperSheet
import kotlinx.coroutines.launch

/**
 * The Today screen: the picture of the day.
 */
@Composable
fun TodayScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArchive: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = containerViewModel { container ->
        TodayViewModel(
            apodRepository = container.apodRepository,
            favouritesRepository = container.favouritesRepository,
            imageRepository = container.imageRepository,
            settingsStore = container.settingsStore,
            shareHelper = container.shareHelper,
            wallpaperStorage = container.wallpaperStorage,
            syncEngine = container.syncEngine,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    var showWallpaperSheet by remember { mutableStateOf(false) }

    val saveImage = rememberLegacyStoragePermission(
        onGranted = { viewModel.saveImage() },
        onDenied = { scope.launch { snackbarHostState.showSnackbar(MESSAGE_PERMISSION_DENIED) } },
    )

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TodayEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                TodayEvent.OpenWallpaperPicker -> openWallpaperPicker(context)
            }
        }
    }

    // Refresh on open when the cached copy is stale; the cached content stays visible.
    LaunchedEffect(Unit) { viewModel.refreshOnOpen() }

    TodayContent(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenEntry = onOpenEntry,
        onOpenSettings = onOpenSettings,
        onToggleFavourite = viewModel::toggleFavourite,
        onSaveImage = saveImage,
        onShare = viewModel::share,
        onRetry = viewModel::retry,
        onOpenArchive = onOpenArchive,
        onOpenWallpaper = { showWallpaperSheet = true },
        modifier = modifier,
    )

    if (showWallpaperSheet) {
        WallpaperSheet(
            scaling = state.scaling,
            updateDaily = state.updateDaily,
            preview = todayWallpaperPreview(state),
            caption = wallpaperCaption(state),
            onDismiss = { showWallpaperSheet = false },
            onScalingChange = viewModel::setScaling,
            onUpdateDailyChange = viewModel::setUpdateDaily,
            onPreviewAndSet = {
                showWallpaperSheet = false
                viewModel.previewAndSetWallpaper()
            },
        )
    }
}

/**
 * The stateless Today screen, driven only by [state] and callbacks.
 *
 * It renders the loading skeleton, the no-cache error and the content (including the offline
 * variant) in the same layout, so the screen never jumps between states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayContent(
    state: TodayUiState,
    onRefresh: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavourite: () -> Unit,
    onSaveImage: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.today_title),
            actions = {
                IconActionButton(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = stringResource(R.string.today_settings),
                    onClick = onOpenSettings,
                    tint = MohakashColors.TextMuted,
                )
            },
        )

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val entry = state.entry
            when {
                entry == null && state.hasError -> TodayErrorState(
                    onRetry = onRetry,
                    onOpenArchive = onOpenArchive,
                )

                entry == null -> TodaySkeleton()
                else -> TodayLoadedContent(
                    state = state,
                    entry = entry,
                    onOpenEntry = onOpenEntry,
                    onToggleFavourite = onToggleFavourite,
                    onSaveImage = onSaveImage,
                    onShare = onShare,
                    onRetry = onRetry,
                    onOpenWallpaper = onOpenWallpaper,
                )
            }
        }
    }
}

/** The skeleton that matches the layout of the loaded screen. */
@Composable
private fun TodaySkeleton() {
    val loadingLabel = stringResource(R.string.today_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .busySemantics(loadingLabel),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(MohakashDimens.TodayHeroHeight),
            cornerRadius = 0.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(26.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(26.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(14.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp),
                    cornerRadius = 24.dp,
                )
            }
            Spacer(Modifier.weight(1f))
            SkeletonBlock(
                modifier = Modifier
                    .width(150.dp)
                    .height(48.dp),
                cornerRadius = 24.dp,
            )
        }
        SkeletonBlock(
            modifier = Modifier
                .padding(start = MohakashDimens.SidePadding, top = 12.dp)
                .width(120.dp)
                .height(12.dp),
        )
    }
}

/** The centred error state that is shown when there is no cached entry at all. */
@Composable
private fun TodayErrorState(
    onRetry: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.today_error_title),
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.today_error_body),
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.today_error_try_again),
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
        )
        SecondaryButton(
            text = stringResource(R.string.today_error_archive),
            onClick = onOpenArchive,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The loaded Today content, including the offline variant. */
@Composable
private fun TodayLoadedContent(
    state: TodayUiState,
    entry: ApodEntry,
    onOpenEntry: (String) -> Unit,
    onToggleFavourite: () -> Unit,
    onSaveImage: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onOpenWallpaper: () -> Unit,
) {
    val isVideo = entry.mediaType.isPlayable
    val posterUrl = entry.thumbnailUrl ?: entry.displayImageUrl
    val wallpaperActive = state.wallpaperMeta?.date == entry.date
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.isOffline) {
            OfflineBanner(
                date = entry.date,
                onRetry = onRetry,
            )
        }

        FullBleedImageFrame(
            url = if (isVideo) posterUrl else entry.displayImageUrl,
            contentDescription = entry.altText ?: entry.title,
            height = if (state.isOffline) {
                MohakashDimens.TodayOfflineHeroHeight
            } else {
                MohakashDimens.TodayHeroHeight
            },
            onClick = { onOpenEntry(entry.date) },
            interactionLabel = stringResource(R.string.today_open_entry),
            isVideo = isVideo,
            // If a video has no poster, the frame keeps the bare glyph.
            videoLabel = if (isVideo && !posterUrl.isNullOrBlank()) {
                stringResource(R.string.detail_video_chip)
            } else {
                null
            },
            unavailableLabel = if (isVideo) null else stringResource(R.string.detail_image_unavailable),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MohakashDimens.SidePadding, end = MohakashDimens.SidePadding, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = creditLine(entry.date, entry.displayCredit),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            Text(
                text = entry.title,
                style = MohakashTextStyles.PageTitle,
                color = MohakashColors.Text,
            )
            Text(
                text = entry.explanation,
                style = MohakashTextStyles.BodyBase,
                color = MohakashColors.TextPreview,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconActionButton(
                painter = painterResource(
                    if (state.isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                ),
                contentDescription = stringResource(R.string.today_save),
                onClick = onToggleFavourite,
                tint = if (state.isFavourite) MohakashColors.Accent else MohakashColors.Text,
            )
            // A video can't be saved as an image, and while offline only the cached copy exists.
            if (!state.isOffline && !isVideo) {
                IconActionButton(
                    painter = painterResource(R.drawable.ic_download_24),
                    contentDescription = stringResource(R.string.today_download),
                    onClick = onSaveImage,
                )
            }
            IconActionButton(
                painter = painterResource(R.drawable.ic_share_24),
                contentDescription = stringResource(R.string.today_share),
                onClick = onShare,
            )
            Spacer(Modifier.weight(1f))
            if (!state.isOffline) {
                WallpaperPill(
                    active = wallpaperActive,
                    onClick = onOpenWallpaper,
                )
            }
        }

        val timeAgo = TimeFormatting.formatSyncedAgo(state.syncedAt, System.currentTimeMillis())
        Text(
            // Without a sync timestamp the line simply reads "Never synced".
            text = if (state.syncedAt == null) {
                timeAgo
            } else {
                stringResource(
                    if (state.isOffline) R.string.last_synced_ago else R.string.synced_ago,
                    timeAgo,
                )
            },
            style = MohakashTextStyles.Caption,
            color = MohakashColors.TextMuted,
            modifier = Modifier.padding(
                start = MohakashDimens.SidePadding,
                end = MohakashDimens.SidePadding,
                top = if (state.isOffline) 8.dp else 12.dp,
                bottom = 24.dp,
            ),
        )
    }
}

/** The banner that is shown when the app works with the cached copy. */
@Composable
private fun OfflineBanner(date: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MohakashDimens.SidePadding, end = MohakashDimens.SidePadding, bottom = 12.dp)
            .clip(RoundedCornerShape(MohakashDimens.BannerRadius))
            .background(MohakashColors.SurfaceStrong)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.offline_banner, date),
            style = MohakashTextStyles.UiSmall,
            color = MohakashColors.Text,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        TextAction(text = stringResource(R.string.offline_retry), onClick = onRetry)
    }
}

/**
 * The "Set live wallpaper" pill in the action row.
 *
 * When the current wallpaper is this entry, the pill turns into an outlined "Wallpaper active"
 * pill. It still opens the sheet.
 */
@Composable
private fun WallpaperPill(active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Box(
        modifier = Modifier
            .heightIn(min = MohakashDimens.TouchTarget)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.border(1.dp, MohakashColors.BorderStrong, shape)
                } else {
                    Modifier.background(MohakashColors.Accent)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (active) R.string.today_wallpaper_active else R.string.today_set_wallpaper,
            ),
            style = MohakashTextStyles.UiSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (active) MohakashColors.Text else MohakashColors.OnAccent,
            textAlign = TextAlign.Center,
        )
    }
}

/** The date and credit line — the credit is omitted when the API has none. */
internal fun creditLine(date: String?, credit: String?): String {
    val cleanDate = date?.takeIf { it.isNotBlank() }.orEmpty()
    val cleanCredit = credit?.takeIf { it.isNotBlank() }
    return when {
        cleanCredit.isNullOrBlank() -> cleanDate
        cleanDate.isBlank() -> cleanCredit
        else -> "$cleanDate · $cleanCredit"
    }
}

/** The caption of the selected picture: `date · title`. */
internal fun wallpaperCaption(state: TodayUiState): String {
    val date = (state.entry?.date ?: state.wallpaperMeta?.date)?.takeIf { it.isNotBlank() }.orEmpty()
    val title = (state.entry?.title ?: state.wallpaperMeta?.title)?.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        date.isBlank() -> title
        title.isBlank() -> date
        else -> "$date · $title"
    }
}

/**
 * The picture the wallpaper sheet should show.
 *
 * It is the entry on screen, not the previously installed picture. The local file is reused only
 * when it already belongs to that entry, so the sheet keeps working offline after it was set.
 */
internal fun todayWallpaperPreview(state: TodayUiState): WallpaperPreview {
    val entry = state.entry ?: return WallpaperPreview(localPath = state.wallpaperPreviewPath)
    val installedUrl = state.wallpaperMeta?.sourceUrl
    val installedIsEntry = state.wallpaperPreviewPath != null &&
        state.wallpaperMeta?.date == entry.date &&
        installedUrl != null &&
        installedUrl in setOfNotNull(entry.imageUrl, entry.hdImageUrl, entry.thumbnailUrl)
    if (installedIsEntry) {
        return WallpaperPreview(localPath = state.wallpaperPreviewPath)
    }

    val remote = if (entry.mediaType.isImage) {
        entry.displayImageUrl
    } else {
        entry.thumbnailUrl ?: entry.displayImageUrl
    }
    return WallpaperPreview(
        localPath = state.wallpaperPreviewPath.takeIf { remote == null },
        remoteUrl = remote,
    )
}

/** Shown when the legacy storage permission is refused. */
private const val MESSAGE_PERMISSION_DENIED = "Storage permission is needed to save the image."
