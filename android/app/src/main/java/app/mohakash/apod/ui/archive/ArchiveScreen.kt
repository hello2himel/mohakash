package app.mohakash.apod.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.ui.components.EllipsizedText
import app.mohakash.apod.ui.components.FilterChipItem
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** The maximum number of year chips in the archive's quick filter row. */
private const val QUICK_YEAR_COUNT = 3

/**
 * The archive screen: every entry of the public archive.
 */
@Composable
fun ArchiveScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        ArchiveViewModel(
            apodRepository = container.apodRepository,
            archiveIndexRepository = container.archiveIndexRepository,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    // The index is shared with the feed and cached on disk, so opening the tab is cheap.
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ArchiveContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onSelectYear = viewModel::selectYear,
        onOpenFilters = { showFilters = true },
        onOpenEntry = onOpenEntry,
        onOpenSettings = onOpenSettings,
        onClearFilters = viewModel::reset,
        onLoadMore = viewModel::loadMore,
        modifier = modifier,
    )

    if (showFilters) {
        FilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onYearSelected = viewModel::selectYear,
            onTypeSelected = viewModel::setTypeFilter,
            onJumpToDate = viewModel::jumpToDate,
            onReset = viewModel::reset,
            onApply = { showFilters = false },
        )
    }
}

/** The stateless archive screen, used by the previews as well. */
@Composable
internal fun ArchiveContent(
    state: ArchiveUiState,
    onQueryChange: (String) -> Unit,
    onSelectYear: (Int) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.visible.size, state.query, state.selectedYear, state.typeFilter) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && state.hasMore && index >= state.visible.size - 3) {
                    onLoadMore()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.archive_title),
            actions = {
                IconActionButton(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = stringResource(R.string.feed_settings),
                    onClick = onOpenSettings,
                    tint = MohakashColors.TextMuted,
                )
            },
        )

        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = MohakashDimens.SidePadding),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = MohakashDimens.SidePadding),
            horizontalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
        ) {
            // Only the quick-pick years live here; the full set is in the Filter sheet.
            items(state.availableYears.take(QUICK_YEAR_COUNT), key = { it }) { year ->
                FilterChipItem(
                    text = year.toString(),
                    selected = year == state.selectedYear,
                    onClick = { onSelectYear(year) },
                )
            }
            item {
                FilterChipItem(
                    text = stringResource(R.string.archive_filters),
                    selected = false,
                    onClick = onOpenFilters,
                )
            }
        }

        when {
            state.isLoading && state.visible.isEmpty() -> ArchiveSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            state.visible.isEmpty() -> ArchiveEmptyState(
                query = state.query,
                onClearFilters = onClearFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = MohakashDimens.SidePadding),
            ) {
                items(state.visible, key = { it.date }) { entry ->
                    ArchiveRow(entry = entry, onClick = { onOpenEntry(entry.date) })
                }
            }
        }
    }
}

/** The search pill above the archive list. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    val hint = stringResource(R.string.archive_search_hint)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MohakashTextStyles.BodyMedium.copy(color = MohakashColors.Text),
        cursorBrush = SolidColor(MohakashColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = hint },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(MohakashColors.Surface)
                    .border(1.dp, MohakashColors.Border, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MohakashTextStyles.BodyMedium,
                        color = MohakashColors.TextMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** The skeleton rows shown while the archive index is loading. */
@Composable
private fun ArchiveSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.archive_loading)
    LazyColumn(
        modifier = modifier.busySemantics(loadingLabel),
        contentPadding = PaddingValues(horizontal = MohakashDimens.SidePadding),
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(MohakashDimens.ArchiveThumbnailWidth)
                        .height(MohakashDimens.ArchiveThumbnailHeight),
                    cornerRadius = MohakashDimens.ThumbnailRadius,
                )
                Spacer(Modifier.width(MohakashDimens.ListRowGap))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp),
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(15.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
        }
    }
}

/** The centred empty result with a way back to the full archive. */
@Composable
private fun ArchiveEmptyState(
    query: String,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = MohakashDimens.SidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (query.isBlank()) {
                stringResource(R.string.archive_empty_filters)
            } else {
                stringResource(R.string.archive_empty_query, query.trim())
            },
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(
            text = stringResource(R.string.archive_clear_filters),
            onClick = onClearFilters,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/** One row of the archive list: thumbnail, date and title. */
@Composable
private fun ArchiveRow(entry: ArchiveIndexEntry, onClick: () -> Unit) {
    val isVideo = entry.mediaType.isPlayable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(MohakashDimens.ArchiveThumbnailWidth)
                .height(MohakashDimens.ArchiveThumbnailHeight),
            contentAlignment = Alignment.Center,
        ) {
            PictureFrame(
                url = entry.thumbnailUrl ?: entry.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = MohakashDimens.ThumbnailRadius,
            )
            if (isVideo) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_24),
                    contentDescription = stringResource(R.string.detail_video_chip),
                    tint = MohakashColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(MohakashDimens.ListRowGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.date,
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            EllipsizedText(
                text = entry.title,
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MohakashColors.Divider),
    )
}
