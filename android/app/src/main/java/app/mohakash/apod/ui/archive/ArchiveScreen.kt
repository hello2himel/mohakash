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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.ui.components.EllipsizedText
import app.mohakash.apod.ui.components.FilterChipItem
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The archive screen: every entry of the public archive.
 */
@Composable
fun ArchiveScreen(
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        ArchiveViewModel(apodRepository = container.apodRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(listState, state.filtered.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && index >= state.visible.size - 5) viewModel.loadMore()
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(title = stringResource(R.string.archive_title))

        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            modifier = Modifier.padding(horizontal = MohakashDimens.SidePadding),
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = MohakashDimens.SidePadding),
            horizontalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
        ) {
            items(state.availableYears, key = { it }) { year ->
                FilterChipItem(
                    text = year.toString(),
                    selected = year == state.selectedYear,
                    onClick = { viewModel.selectYear(year) },
                )
            }
            item {
                FilterChipItem(
                    text = stringResource(R.string.archive_filters),
                    selected = false,
                    onClick = { showFilters = true },
                )
            }
        }

        if (state.visible.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MohakashDimens.SidePadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.archive_empty),
                    style = MohakashTextStyles.BodyMedium,
                    color = MohakashColors.TextMuted,
                )
            }
        } else {
            LazyColumn(
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

/** The search pill above the archive list. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MohakashTextStyles.BodyMedium.copy(color = MohakashColors.Text),
        cursorBrush = SolidColor(MohakashColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
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
                        text = stringResource(R.string.archive_search_hint),
                        style = MohakashTextStyles.BodyMedium,
                        color = MohakashColors.TextMuted,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** One row of the archive list: thumbnail, date and title. */
@Composable
private fun ArchiveRow(entry: ArchiveIndexEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PictureFrame(
            url = entry.thumbnailUrl ?: entry.imageUrl,
            contentDescription = stringResource(R.string.archive_thumbnail),
            modifier = Modifier
                .width(MohakashDimens.ArchiveThumbnailWidth)
                .height(MohakashDimens.ArchiveThumbnailHeight),
            cornerRadius = 8.dp,
        )
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
                style = MohakashTextStyles.UiSmall,
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
