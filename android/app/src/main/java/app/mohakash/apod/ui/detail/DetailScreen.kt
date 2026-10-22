package app.mohakash.apod.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.FullBleedImageFrame
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.LinkRow
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
import kotlinx.coroutines.launch

/**
 * The detail screen of one entry.
 *
 * Image entries can be saved and used as a live wallpaper; video entries only link to the
 * original page.
 */
@Composable
fun DetailScreen(
    date: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel = containerViewModel(key = "detail-$date") { container ->
        DetailViewModel(
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
        )
    }
}

/** The stateless detail screen, used by the previews as well. */
@Composable
internal fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onShare: () -> Unit,
    onSaveImage: () -> Unit,
    onOpenLink: (String) -> Unit,
    onSetWallpaper: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = state.entry
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = "",
            onBack = onBack,
            actions = {
                IconActionButton(
                    painter = painterResource(
                        if (state.isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                    ),
                    contentDescription = stringResource(R.string.today_save),
                    onClick = onToggleFavourite,
                    tint = if (state.isFavourite) MohakashColors.Accent else MohakashColors.Text,
                )
                IconActionButton(
                    painter = painterResource(R.drawable.ic_share_24),
                    contentDescription = stringResource(R.string.today_share),
                    onClick = onShare,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                entry == null && state.hasError -> DetailErrorState(onRetry = onRetry)
                entry == null -> DetailSkeleton()
                else -> DetailLoadedContent(
                    entry = entry,
                    onSaveImage = onSaveImage,
                    onOpenLink = onOpenLink,
                    onSetWallpaper = onSetWallpaper,
                )
            }
        }
    }
}

/** The skeleton that matches the layout of a loaded entry. */
@Composable
private fun DetailSkeleton() {
    val loadingLabel = stringResource(R.string.detail_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .busySemantics(loadingLabel),
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
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(160.dp)
                    .height(12.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(26.dp),
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(26.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 14.dp),
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
                .padding(top = 16.dp),
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
private fun DetailErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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

/** The loaded content of an entry: image or video poster, actions, text and links. */
@Composable
private fun DetailLoadedContent(
    entry: ApodEntry,
    onSaveImage: () -> Unit,
    onOpenLink: (String) -> Unit,
    onSetWallpaper: () -> Unit,
) {
    val isVideo = entry.mediaType.isPlayable
    val imageUnavailable = stringResource(R.string.detail_image_unavailable)

    if (isVideo) {
        val posterUrl = entry.thumbnailUrl ?: entry.displayImageUrl
        FullBleedImageFrame(
            url = posterUrl,
            contentDescription = entry.altText,
            height = MohakashDimens.VideoPosterHeight,
            isVideo = true,
            // If a video has no poster, the design wants the bare glyph — no chip, no error label.
            videoLabel = if (!posterUrl.isNullOrBlank()) {
                stringResource(R.string.detail_video_chip)
            } else {
                null
            },
            playContentDescription = null,
            unavailableLabel = null,
        )
    } else {
        FullBleedImageFrame(
            url = entry.displayImageUrl,
            contentDescription = entry.altText,
            height = MohakashDimens.DetailHeroHeight,
            unavailableLabel = imageUnavailable,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = detailCreditLine(entry),
            style = MohakashTextStyles.Caption,
            color = MohakashColors.TextMuted,
        )
        Text(
            text = entry.title,
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
        )
    }

    if (isVideo) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 14.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.detail_watch_original),
                onClick = { entry.apodUrl?.let(onOpenLink) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NoteBox(
            text = stringResource(R.string.detail_video_note),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 14.dp),
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.detail_save_image),
                onClick = onSaveImage,
                modifier = Modifier.weight(1f),
            )
            SecondaryWallpaperButton(
                onClick = onSetWallpaper,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entry.paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MohakashTextStyles.BodyMedium,
                color = MohakashColors.TextBody,
            )
        }
    }

    // The video design (2.6) has no link section; its one link is the watch button above.
    if (isVideo) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 14.dp, bottom = 24.dp),
    ) {
        entry.displayCredit?.let { credit ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
            Text(
                text = stringResource(R.string.detail_image_credit, credit),
                style = MohakashTextStyles.Helper,
                color = MohakashColors.TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
            )
        }
        entry.apodUrl?.let { url ->
            LinkRow(
                text = stringResource(R.string.detail_original_page),
                onClick = { onOpenLink(url) },
                showDivider = true,
            )
        }
        if (!isVideo) {
            entry.hdImageUrl?.let { url ->
                LinkRow(
                    text = stringResource(R.string.detail_hd_image),
                    onClick = { onOpenLink(url) },
                )
            }
        }
    }
}

/** The outlined *Set as wallpaper* button of the detail screen. */
@Composable
private fun SecondaryWallpaperButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_set_wallpaper),
            style = MohakashTextStyles.UiSmall,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
        )
    }
}

/** The credit line of the detail screen: `date · Credit: creator`. */
internal fun detailCreditLine(entry: ApodEntry?): String {
    if (entry == null) return ""
    val credit = entry.displayCredit
    val date = entry.date.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        credit.isNullOrBlank() -> date
        date.isBlank() -> "Credit: $credit"
        else -> "$date · Credit: $credit"
    }
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
