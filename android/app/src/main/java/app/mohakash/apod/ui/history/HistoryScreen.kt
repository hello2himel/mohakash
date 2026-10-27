package app.mohakash.apod.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.history.HistoryItem
import app.mohakash.apod.history.HistorySource
import app.mohakash.apod.ui.components.EllipsizedText
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import app.mohakash.apod.wallpaper.WallpaperStatus

/** Discovery history: random picks and carousel wallpaper choices, never ordinary APOD browsing. */
@Composable
fun HistoryScreen(
    onOpenEntry: (String) -> Unit,
    onBrowseFeed: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = containerViewModel { container ->
        HistoryViewModel(
            historyRepository = container.historyRepository,
            settingsStore = container.settingsStore,
            selectionStore = container.carouselSelectionStore,
            nextRunStore = container.carouselNextRunStore,
            carouselController = container.carouselController,
            isWallpaperActive = { WallpaperStatus.isActive(appContext) },
        )
    }
    val items by viewModel.items.collectAsStateWithLifecycle()
    val nextWallpaper by viewModel.nextWallpaper.collectAsStateWithLifecycle()

    HistoryContent(
        items = items,
        nextWallpaper = nextWallpaper,
        onSkipWallpaper = viewModel::skipWallpaper,
        onOpenEntry = onOpenEntry,
        onBrowseFeed = onBrowseFeed,
        modifier = modifier,
        bottomContentPadding = bottomContentPadding,
    )
}

@Composable
internal fun HistoryContent(
    items: List<HistoryItem>,
    nextWallpaper: NextWallpaperUi? = null,
    onSkipWallpaper: () -> Unit = {},
    onOpenEntry: (String) -> Unit,
    onBrowseFeed: () -> Unit,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (nextWallpaper != null) {
            NextWallpaperHeader(
                state = nextWallpaper,
                onSkip = onSkipWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MohakashDimens.SidePadding)
                    .padding(top = 4.dp, bottom = 10.dp),
            )
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                HistoryEmptyState(onBrowseFeed = onBrowseFeed)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = MohakashDimens.SidePadding,
                    end = MohakashDimens.SidePadding,
                    top = 0.dp,
                    bottom = bottomContentPadding + 16.dp,
                ),
            ) {
                items(items, key = { "${it.source.name}-${it.date}" }) { item ->
                    HistoryRow(item = item, onClick = { onOpenEntry(item.date) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PictureFrame(
            url = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier.size(76.dp),
            cornerRadius = MohakashDimens.CardRadius,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SourceBadge(source = item.source)
            Text(
                text = item.date,
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            EllipsizedText(
                text = item.title,
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.Text,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_24),
            contentDescription = null,
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SourceBadge(source: HistorySource) {
    val shape = RoundedCornerShape(6.dp)
    val label = when (source) {
        HistorySource.RANDOM -> stringResource(R.string.history_source_random)
        HistorySource.CAROUSEL -> stringResource(R.string.history_source_carousel)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MohakashColors.Accent.copy(alpha = 0.12f))
            .border(1.dp, MohakashColors.Accent.copy(alpha = 0.25f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MohakashTextStyles.Caption.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = MohakashColors.Accent,
        )
    }
}

@Composable
private fun HistoryEmptyState(onBrowseFeed: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_archive_24),
            contentDescription = null,
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MohakashTextStyles.SectionHeading,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.history_browse_feed),
            onClick = onBrowseFeed,
            modifier = Modifier
                .width(200.dp)
                .padding(top = 22.dp),
            height = 48.dp,
        )
    }
}


/** A compact countdown/progress header for the active carousel. */
@Composable
private fun NextWallpaperHeader(
    state: NextWallpaperUi,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val progress = state.progress
    val countdown = when {
        state.remainingMillis > 0L -> stringResource(
            R.string.history_next_wallpaper,
            formatCountdown(state.remainingMillis),
        )
        progress == null -> stringResource(R.string.history_next_wallpaper_preparing)
        else -> stringResource(R.string.history_next_wallpaper_updating)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(MohakashColors.Surface)
            .border(1.dp, MohakashColors.Border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_next_wallpaper_label),
                style = MohakashTextStyles.UiSmallSemiBold,
                color = MohakashColors.Text,
                modifier = Modifier.weight(1f),
            )
            TextAction(
                text = stringResource(R.string.history_skip_wallpaper),
                onClick = onSkip,
                minHeight = 40.dp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            text = countdown,
            style = MohakashTextStyles.Helper,
            color = MohakashColors.TextMuted,
        )
        if (progress == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MohakashColors.SurfaceStrong),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MohakashColors.SurfaceStrong)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(MohakashColors.AccentLight, MohakashColors.AccentDeep),
                            ),
                        ),
                )
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "%dh %02dm".format(hours, minutes)
        minutes > 0L -> "%dm %02ds".format(minutes, seconds)
        else -> "%ds".format(seconds)
    }
}
