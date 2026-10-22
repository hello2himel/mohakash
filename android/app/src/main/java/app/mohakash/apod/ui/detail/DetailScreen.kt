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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.LinkRow
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.NoteBox
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.rememberLegacyStoragePermission
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.navigation.openWallpaperPicker
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import app.mohakash.apod.ui.today.creditLine
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

    val entry = state.entry
    val isVideo = entry?.mediaType?.isPlayable == true

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
                    onClick = viewModel::toggleFavourite,
                    tint = if (state.isFavourite) MohakashColors.Accent else MohakashColors.Text,
                )
                IconActionButton(
                    painter = painterResource(R.drawable.ic_share_24),
                    contentDescription = stringResource(R.string.today_share),
                    onClick = viewModel::share,
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (isVideo) {
                VideoPoster(
                    entry = entry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MohakashDimens.VideoPosterHeight),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MohakashDimens.DetailHeroHeight)
                        .clickable { entry?.let { onOpenLink(it.apodUrl.orEmpty()) } },
                ) {
                    PictureFrame(
                        url = entry?.displayImageUrl,
                        contentDescription = entry?.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp,
                        borderColor = null,
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Border),
            )

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
                    text = entry?.title.orEmpty(),
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
                        onClick = { entry?.apodUrl?.let(onOpenLink) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
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
                        onClick = saveImage,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    )
                    SecondaryWallpaperButton(
                        onClick = { showWallpaperSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
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
                entry?.paragraphs?.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MohakashTextStyles.BodyMedium,
                        color = MohakashColors.TextBody,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MohakashDimens.SidePadding)
                    .padding(top = 14.dp, bottom = 24.dp),
            ) {
                entry?.apodUrl?.let { url ->
                    LinkRow(
                        text = stringResource(R.string.detail_original_page),
                        onClick = { onOpenLink(url) },
                        showDivider = false,
                    )
                }
                if (!isVideo) {
                    entry?.hdImageUrl?.let { url ->
                        LinkRow(
                            text = stringResource(R.string.detail_hd_image),
                            onClick = { onOpenLink(url) },
                        )
                    }
                }
            }
        }
    }

    if (showWallpaperSheet) {
        WallpaperSheet(
            scaling = state.scaling,
            updateDaily = state.updateDaily,
            previewPath = state.wallpaperPreviewPath,
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

/** The poster of a video entry with the play glyph and the *Video* chip. */
@Composable
private fun VideoPoster(entry: ApodEntry?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MohakashColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        PictureFrame(
            url = entry?.thumbnailUrl ?: entry?.displayImageUrl,
            contentDescription = stringResource(R.string.detail_video_poster),
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            borderColor = null,
        )
        Icon(
            painter = painterResource(R.drawable.ic_play_24),
            contentDescription = null,
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(56.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MohakashColors.Background)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_video_chip),
                style = MohakashTextStyles.Caption.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
                color = MohakashColors.Text,
            )
        }
    }
}

/** The outlined *Set as wallpaper* button of the detail screen. */
@Composable
private fun SecondaryWallpaperButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_set_wallpaper),
            style = MohakashTextStyles.UiSmall,
            color = MohakashColors.Text,
            maxLines = 1,
        )
    }
}

/** The credit line of the detail screen: `date · Credit: creator`. */
private fun detailCreditLine(entry: ApodEntry?): String {
    if (entry == null) return ""
    val credit = entry.displayCredit
    val date = entry.date.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        credit.isNullOrBlank() -> date
        date.isBlank() -> "Credit: $credit"
        else -> "$date · Credit: $credit"
    }
}

/** The caption of the current wallpaper picture. */
private fun wallpaperCaptionForDetail(state: DetailUiState): String {
    val meta = state.wallpaperMeta ?: return "—"
    val date = meta.date.takeIf { it.isNotBlank() }.orEmpty()
    val title = meta.title.takeIf { it.isNotBlank() }.orEmpty()
    return when {
        date.isBlank() -> title
        title.isBlank() -> date
        else -> "$date · $title"
    }
}

/** Shown when the legacy storage permission is refused. */
private const val MESSAGE_PERMISSION_DENIED = "Storage permission is needed to save the image."
