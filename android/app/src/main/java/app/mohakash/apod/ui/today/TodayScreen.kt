package app.mohakash.apod.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.rememberLegacyStoragePermission
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.navigation.openWallpaperPicker
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isOffline) {
                OfflineBanner(
                    date = state.entry?.date.orEmpty(),
                    onRetry = viewModel::retry,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MohakashDimens.TodayHeroHeight)
                    .clickable { state.entry?.let { onOpenEntry(it.date) } },
            ) {
                PictureFrame(
                    url = state.entry?.displayImageUrl,
                    contentDescription = state.entry?.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0.dp,
                    borderColor = null,
                )
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
                    .padding(start = MohakashDimens.SidePadding, end = MohakashDimens.SidePadding, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val entry = state.entry
                Text(
                    text = creditLine(entry?.date, entry?.displayCredit),
                    style = MohakashTextStyles.Caption,
                    color = MohakashColors.TextMuted,
                )
                Text(
                    text = entry?.title.orEmpty(),
                    style = MohakashTextStyles.PageTitle,
                    color = MohakashColors.Text,
                )
                Text(
                    text = entry?.explanation.orEmpty(),
                    style = MohakashTextStyles.BodyMedium,
                    color = MohakashColors.TextBody,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MohakashDimens.SidePadding)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconActionButton(
                    painter = painterResource(
                        if (state.isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                    ),
                    contentDescription = stringResource(R.string.today_save),
                    onClick = viewModel::toggleFavourite,
                    tint = if (state.isFavourite) MohakashColors.Accent else MohakashColors.Text,
                )
                IconActionButton(
                    painter = painterResource(R.drawable.ic_download_24),
                    contentDescription = stringResource(R.string.today_download),
                    onClick = saveImage,
                )
                IconActionButton(
                    painter = painterResource(R.drawable.ic_share_24),
                    contentDescription = stringResource(R.string.today_share),
                    onClick = viewModel::share,
                )
                Spacer(Modifier.weight(1f))
                WallpaperPill(onClick = { showWallpaperSheet = true })
            }

            Text(
                text = stringResource(R.string.synced_ago, TimeFormatting.formatSyncedAgo(state.syncedAt, System.currentTimeMillis())),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
                modifier = Modifier.padding(
                    start = MohakashDimens.SidePadding,
                    end = MohakashDimens.SidePadding,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
            )
        }
    }

    if (showWallpaperSheet) {
        WallpaperSheet(
            scaling = state.scaling,
            updateDaily = state.updateDaily,
            previewPath = state.wallpaperPreviewPath,
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

/** The banner that is shown when the app works with the cached copy. */
@Composable
private fun OfflineBanner(date: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 12.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(MohakashDimens.CardRadius))
            .background(MohakashColors.SurfaceStrong)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.offline_banner, date),
            style = MohakashTextStyles.UiSmall,
            color = MohakashColors.Text,
            modifier = Modifier.weight(1f),
        )
        TextAction(text = stringResource(R.string.offline_retry), onClick = onRetry)
    }
}

/** The "Set live wallpaper" pill in the action row. */
@Composable
private fun WallpaperPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(MohakashDimens.TouchTarget)
            .clip(RoundedCornerShape(MohakashDimens.PillRadius))
            .background(MohakashColors.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.today_set_wallpaper),
            style = MohakashTextStyles.UiSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = MohakashColors.OnAccent,
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

/** The caption of the current wallpaper picture: `date · title`. */
internal fun wallpaperCaption(state: TodayUiState): String {
    val meta = state.wallpaperMeta
    if (meta == null) return "—"
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
