package app.mohakash.apod.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.NoteBox
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.components.rememberLegacyStoragePermission
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.navigation.openWallpaperPicker
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import app.mohakash.apod.ui.wallpaper.WallpaperSheet
import app.mohakash.apod.wallpaper.WallpaperStatus
import kotlinx.coroutines.launch

/**
 * The detail screen of one entry: an uncropped, zoomable picture, the complete explanation and
 * every metadata field, plus the save/wallpaper/favourite/share actions.
 *
 * The wallpaper sheet and the legacy storage permission flow are unchanged; the public signature
 * is frozen so the navigation graph needs no edit when the feed is rebuilt.
 */
@Composable
fun DetailScreen(
    date: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    var systemWallpaperActive by remember { mutableStateOf(WallpaperStatus.isActive(appContext)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        systemWallpaperActive = WallpaperStatus.isActive(appContext)
    }
    val viewModel = containerViewModel(key = "detail-$date") { container ->
        DetailViewModel(
            apodRepository = container.apodRepository,
            favouritesRepository = container.favouritesRepository,
            imageRepository = container.imageRepository,
            settingsStore = container.settingsStore,
            shareHelper = container.shareHelper,
            wallpaperStorage = container.wallpaperStorage,
            syncEngine = container.syncEngine,
            syncController = container.syncController,
            isWallpaperActive = { WallpaperStatus.isActive(appContext) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var showWallpaperSheet by remember { mutableStateOf(false) }

    val saveImage = rememberLegacyStoragePermission(
        onGranted = { viewModel.saveImage() },
        onDenied = { scope.launch { snackbarHostState.showSnackbar(MESSAGE_PERMISSION_DENIED) } },
    )

    LaunchedEffect(date) { viewModel.load(date) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                DetailEvent.OpenWallpaperPicker -> openWallpaperPicker(context)
            }
        }
    }

    DetailContent(
        state = state,
        onBack = onBack,
        onToggleFavourite = viewModel::toggleFavourite,
        onShare = viewModel::share,
        onSaveImage = saveImage,
        onOpenLink = onOpenLink,
        onOpenSettings = onOpenSettings,
        onSetWallpaper = { showWallpaperSheet = true },
        onRetry = viewModel::retry,
        modifier = modifier,
    )

    if (showWallpaperSheet) {
        WallpaperSheet(
            scaling = state.scaling,
            updateDaily = state.updateDaily,
            preview = detailWallpaperPreview(state),
            caption = wallpaperCaptionForDetail(state),
            onDismiss = { showWallpaperSheet = false },
            onScalingChange = viewModel::setScaling,
            onUpdateDailyChange = viewModel::setUpdateDaily,
            onPreviewAndSet = {
                showWallpaperSheet = false
                viewModel.setAsWallpaper()
            },
            isWallpaperActive = systemWallpaperActive,
        )
    }
}

/**
 * The stateless detail screen, used by the previews as well.
 *
 * @param imagePreviewAspectRatio preview-only override of the picture ratio, because the IDE
 *   preview does not load network images.
 */
@Composable
internal fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onSaveImage: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSetWallpaper: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    imagePreviewAspectRatio: Float? = null,
) {
    val entry = state.entry
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        DetailTopBar(
            isFavourite = state.isFavourite,
            onBack = onBack,
            onToggleFavourite = onToggleFavourite,
            onShare = onShare,
            onOpenSettings = onOpenSettings,
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val viewportHeight = maxHeight
            when {
                entry == null && state.hasError -> DetailErrorState(
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )

                entry == null -> DetailSkeleton(modifier = Modifier.fillMaxSize())

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EntryImageViewer(
                        url = entry.pictureUrl(),
                        contentDescription = entry.altText?.takeIf { it.isNotBlank() } ?: entry.title,
                        isVideo = entry.mediaType.isPlayable,
                        viewportHeight = viewportHeight,
                        previewAspectRatio = imagePreviewAspectRatio,
                        modifier = Modifier
                            .padding(horizontal = MohakashDimens.SidePadding)
                            .padding(top = 12.dp),
                    )
                    EntryHeading(entry)
                    if (EntryDetailFormatting.offlineNoteVisible(state.isShowingCachedCopy, entry)) {
                        NoteBox(
                            text = stringResource(R.string.entry_offline_note),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MohakashDimens.SidePadding)
                                .padding(top = 14.dp),
                        )
                    }
                    EntryActionBar(
                        entry = entry,
                        onSaveImage = onSaveImage,
                        onSetWallpaper = onSetWallpaper,
                        onOpenLink = onOpenLink,
                    )
                    EntryExplanation(entry)
                    EntryMetaSection(entry = entry, onOpenLink = onOpenLink)
                    // Keep the last card above the gesture bar, even when the app draws edge to
                    // edge and no bottom navigation bar is reserved on this destination.
                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(28.dp),
                    )
                }
            }
        }
    }
}

/** The top bar: back, favourite, share and the settings gear kept from 1.0.4. */
@Composable
private fun DetailTopBar(
    isFavourite: Boolean,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    MohakashTopBar(
        title = "",
        onBack = onBack,
        actions = {
            IconActionButton(
                painter = painterResource(
                    if (isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                ),
                contentDescription = stringResource(
                    if (isFavourite) R.string.entry_action_unsave else R.string.entry_action_save,
                ),
                onClick = onToggleFavourite,
                tint = if (isFavourite) MohakashColors.Accent else MohakashColors.Text,
            )
            IconActionButton(
                painter = painterResource(R.drawable.ic_share_24),
                contentDescription = stringResource(R.string.entry_action_share),
                onClick = onShare,
            )
            IconActionButton(
                painter = painterResource(R.drawable.ic_settings_24),
                contentDescription = stringResource(R.string.entry_action_settings),
                onClick = onOpenSettings,
                tint = MohakashColors.TextMuted,
            )
        },
    )
}

/** The skeleton that matches the layout of a loaded entry. */
@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.detail_loading)
    Column(
        modifier = modifier.busySemantics(loadingLabel),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(MohakashDimens.DetailHeroHeight),
            cornerRadius = 0.dp,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 20.dp),
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
                    .fillMaxWidth(0.55f)
                    .height(16.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                cornerRadius = 24.dp,
            )
            SkeletonBlock(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                cornerRadius = 24.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(4) { index ->
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 3) 0.6f else 1f)
                        .height(14.dp),
                )
            }
        }
    }
}

/** The centred error state of the detail screen. */
@Composable
private fun DetailErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_error_title),
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.detail_error_body),
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.detail_error_try_again),
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/** The picture (or video poster) URL of the entry, preferring the full-resolution image. */
private fun ApodEntry.pictureUrl(): String? {
    val primary = displayImageUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (mediaType.isImage) return primary
    return thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() } ?: primary
}

/** The caption of the selected picture: `date · title`. */
private fun wallpaperCaptionForDetail(state: DetailUiState): String {
    val date = (state.entry?.date ?: state.wallpaperMeta?.date)?.takeIf { it.isNotBlank() }.orEmpty()
    val title = (state.entry?.title ?: state.wallpaperMeta?.title)?.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        date.isBlank() -> title
        title.isBlank() -> date
        else -> "$date · $title"
    }
}

/**
 * The picture the wallpaper sheet should show: this entry, not the previously installed one.
 *
 * The local file is used when it already belongs to this entry, so the preview still works
 * offline after the wallpaper was set.
 */
private fun detailWallpaperPreview(state: DetailUiState): WallpaperPreview {
    val entry = state.entry ?: return WallpaperPreview(localPath = state.wallpaperPreviewPath)
    val installedUrl = state.wallpaperMeta?.sourceUrl
    val installedIsEntry = state.wallpaperPreviewPath != null &&
        state.wallpaperMeta?.date == entry.date &&
        installedUrl != null &&
        installedUrl in setOfNotNull(entry.imageUrl, entry.hdImageUrl, entry.thumbnailUrl)
    if (installedIsEntry) {
        return WallpaperPreview(localPath = state.wallpaperPreviewPath)
    }
    return WallpaperPreview(remoteUrl = entry.displayImageUrl)
}

/** Shown when the legacy storage permission is refused. */
private const val MESSAGE_PERMISSION_DENIED = "Storage permission is needed to save the image."
