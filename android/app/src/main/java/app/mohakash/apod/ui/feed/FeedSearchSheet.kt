package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.DateRangeDialog
import app.mohakash.apod.ui.components.EllipsizedText
import app.mohakash.apod.ui.components.dateRangeLabel
import app.mohakash.apod.ui.components.isoFromMillis
import app.mohakash.apod.ui.components.FilterChipItem
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The full-height search sheet.
 *
 * It is deliberately not a route: search reuses the feed's ViewModel, the feed's index and the
 * feed's filter type, so a result can be tapped and the pager simply moves to that date. With a
 * cached index, search keeps working offline — a useful consequence of caching the index on disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSearchSheet(
    state: FeedSearchState,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (FeedFilters) -> Unit,
    onSelectResult: (String) -> Unit,
    onGoToDate: (String) -> Unit,
    onRetryIndex: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohakashColors.Surface,
        contentColor = MohakashColors.Text,
        shape = RoundedCornerShape(
            topStart = MohakashDimens.SheetRadius,
            topEnd = MohakashDimens.SheetRadius,
        ),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MohakashColors.BorderStrong),
            )
        },
    ) {
        FeedSearchSheetContent(
            state = state,
            availableYears = availableYears,
            onQueryChange = onQueryChange,
            onFiltersChange = onFiltersChange,
            onSelectResult = onSelectResult,
            onGoToDate = onGoToDate,
            onDismiss = onDismiss,
            onRetryIndex = onRetryIndex,
        )
    }
}

/** The stateless body of the search sheet — used by the preview as well. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedSearchSheetContent(
    state: FeedSearchState,
    availableYears: List<Int>,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (FeedFilters) -> Unit,
    onSelectResult: (String) -> Unit,
    onGoToDate: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetryIndex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilterPanel by rememberSaveable { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var showGoToDatePicker by rememberSaveable { mutableStateOf(false) }
    val activeFilterCount =
        (if (state.filters.year != null) 1 else 0) +
            (if (state.filters.media != FeedMediaFilter.ALL) 1 else 0) +
            (if (state.filters.hasDateRange) 1 else 0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .padding(horizontal = MohakashDimens.SidePadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            IconActionButton(
                painter = painterResource(R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.search_close),
                onClick = onDismiss,
                tint = MohakashColors.TextMuted,
            )
        }

        // The primary search action is not hidden behind a filter sheet: one tap opens a
        // single-date picker and jumps straight to that APOD.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .background(MohakashColors.Accent.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    MohakashColors.Accent.copy(alpha = 0.55f),
                    RoundedCornerShape(percent = 50),
                )
                .clickable { showGoToDatePicker = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_24),
                contentDescription = null,
                tint = MohakashColors.Accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.filter_jump_to_date),
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "→",
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Accent,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChipItem(
                text = stringResource(R.string.archive_filters),
                selected = showFilterPanel || activeFilterCount > 0,
                onClick = { showFilterPanel = !showFilterPanel },
            )
        }

        if (showFilterPanel) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MohakashDimens.CardRadius))
                    .background(MohakashColors.Background)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
                ) {
                    item(key = "all") {
                        FilterChipItem(
                            text = stringResource(R.string.filter_all_years),
                            selected = state.filters.year == null,
                            onClick = { onFiltersChange(state.filters.copy(year = null)) },
                        )
                    }
                    items(availableYears, key = { it }) { year ->
                        FilterChipItem(
                            text = year.toString(),
                            selected = year == state.filters.year,
                            onClick = { onFiltersChange(state.filters.copy(year = year)) },
                        )
                    }
                }
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.filter_all),
                        stringResource(R.string.filter_images),
                        stringResource(R.string.filter_videos),
                    ),
                    selectedIndex = when (state.filters.media) {
                        FeedMediaFilter.ALL -> 0
                        FeedMediaFilter.IMAGES -> 1
                        FeedMediaFilter.VIDEOS -> 2
                    },
                    onSelect = { index ->
                        onFiltersChange(
                            state.filters.copy(
                                media = when (index) {
                                    1 -> FeedMediaFilter.IMAGES
                                    2 -> FeedMediaFilter.VIDEOS
                                    else -> FeedMediaFilter.ALL
                                },
                            ),
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(MohakashDimens.CardRadius))
                        .border(1.dp, MohakashColors.BorderStrong, RoundedCornerShape(MohakashDimens.CardRadius))
                        .clickable { showDateRangePicker = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_24),
                        contentDescription = null,
                        tint = MohakashColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = dateRangeLabel(
                            state.filters.fromDate,
                            state.filters.toDate,
                            stringResource(R.string.date_picker_any),
                        ),
                        style = MohakashTextStyles.UiSmall,
                        color = if (state.filters.hasDateRange) MohakashColors.Text else MohakashColors.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeFilterCount > 0) {
                        TextAction(
                            text = stringResource(R.string.filter_reset),
                            onClick = { onFiltersChange(FeedFilters()) },
                        )
                    }
                }
            }
        }

        when {
            state.isUnavailable -> SearchMessage(
                title = stringResource(R.string.search_offline),
                actionLabel = stringResource(R.string.feed_retry),
                onAction = onRetryIndex,
            )

            state.isSearching && state.results.isEmpty() -> SearchSkeleton()

            state.query.isBlank() && !state.filters.isActive -> SearchMessage(
                title = stringResource(R.string.search_hint),
                actionLabel = null,
                onAction = null,
            )

            state.results.isEmpty() -> SearchMessage(
                title = if (state.query.isBlank()) {
                    stringResource(R.string.search_no_matches_filter)
                } else {
                    stringResource(R.string.search_no_results, state.query.trim())
                },
                actionLabel = null,
                onAction = null,
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.results, key = { it.date }) { item ->
                    SearchResultRow(
                        item = item,
                        onClick = { onSelectResult(item.date) },
                    )
                }
                if (state.results.size >= FeedPaging.SEARCH_RESULT_LIMIT) {
                    item(key = "refine") {
                        Text(
                            text = stringResource(R.string.search_refine),
                            style = MohakashTextStyles.Helper,
                            color = MohakashColors.TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showGoToDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = null)
        DatePickerDialog(
            onDismissRequest = { showGoToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onGoToDate(isoFromMillis(millis))
                    }
                    showGoToDatePicker = false
                }) { Text(stringResource(R.string.filter_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToDatePicker = false }) {
                    Text(stringResource(R.string.clear_cache_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDateRangePicker) {
        DateRangeDialog(
            initialFrom = state.filters.fromDate,
            initialTo = state.filters.toDate,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { from, to ->
                onFiltersChange(state.filters.copy(fromDate = from, toDate = to))
                showDateRangePicker = false
            },
        )
    }
}

/** The search field pill. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    val hint = stringResource(R.string.search_hint)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MohakashTextStyles.BodyMedium.copy(color = MohakashColors.Text),
        cursorBrush = SolidColor(MohakashColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(MohakashColors.Background)
                    .border(1.dp, MohakashColors.Border, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_24),
                    contentDescription = null,
                    tint = MohakashColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = MohakashTextStyles.BodyMedium,
                            color = MohakashColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

/** One search result: thumbnail, ISO date, title and the video glyph. */
@Composable
private fun SearchResultRow(item: FeedDateItem, onClick: () -> Unit) {
    val entry = item.indexEntry
    val isVideo = entry?.mediaType?.isPlayable == true
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
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
                    url = entry?.thumbnailUrl ?: entry?.imageUrl,
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
                    text = item.date,
                    style = MohakashTextStyles.Caption,
                    color = MohakashColors.TextMuted,
                )
                EllipsizedText(
                    text = entry?.title.orEmpty(),
                    style = MohakashTextStyles.UiMedium,
                    color = MohakashColors.Text,
                )
                entry?.displayCredit?.let { credit ->
                    EllipsizedText(
                        text = credit,
                        style = MohakashTextStyles.Helper,
                        color = MohakashColors.TextMuted,
                    )
                }
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

/** The skeleton rows shown while the first query is running. */
@Composable
private fun SearchSkeleton() {
    val loadingLabel = stringResource(R.string.feed_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .busySemantics(loadingLabel),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
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
                        Modifier
                            .width(80.dp)
                            .height(12.dp),
                    )
                    SkeletonBlock(
                        Modifier
                            .fillMaxWidth()
                            .height(15.dp),
                    )
                }
            }
        }
    }
}

/** A centred message with an optional action (offline, empty query, no results). */
@Composable
private fun SearchMessage(title: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            SecondaryButton(text = actionLabel, onClick = onAction)
        }
    }
}
