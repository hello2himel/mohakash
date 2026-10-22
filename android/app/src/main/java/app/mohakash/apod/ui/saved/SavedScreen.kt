package app.mohakash.apod.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.components.EllipsizedText
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.PictureFrame
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The saved screen: the pictures the user kept.
 */
@Composable
fun SavedScreen(
    onOpenEntry: (String) -> Unit,
    onBrowseArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        SavedViewModel(favouritesRepository = container.favouritesRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    SavedContent(
        state = state,
        onOpenEntry = onOpenEntry,
        onBrowseArchive = onBrowseArchive,
        modifier = modifier,
    )
}

/** The stateless saved screen, used by the previews as well. */
@Composable
internal fun SavedContent(
    state: SavedUiState,
    onOpenEntry: (String) -> Unit,
    onBrowseArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.saved_title),
            actions = {
                if (state.entries.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.saved_count,
                            state.entries.size,
                            state.entries.size,
                        ),
                        style = MohakashTextStyles.Helper,
                        color = MohakashColors.TextMuted,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            },
        )

        when {
            state.isLoading -> SavedSkeleton()
            state.entries.isEmpty() -> SavedEmptyState(onBrowseArchive = onBrowseArchive)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = MohakashDimens.SidePadding,
                    end = MohakashDimens.SidePadding,
                    top = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.entries, key = { it.date }) { entry ->
                    SavedTile(entry = entry, onClick = { onOpenEntry(entry.date) })
                }
            }
        }
    }
}

/** One square tile of the saved grid. */
@Composable
private fun SavedTile(entry: ApodEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PictureFrame(
            url = entry.displayImageUrl,
            contentDescription = entry.altText,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = MohakashDimens.CardRadius,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
}

/** The skeleton grid shown while the local database is opening. */
@Composable
private fun SavedSkeleton() {
    val loadingLabel = stringResource(R.string.saved_loading)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .busySemantics(loadingLabel),
        contentPadding = PaddingValues(
            start = MohakashDimens.SidePadding,
            end = MohakashDimens.SidePadding,
            top = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(4) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = MohakashDimens.CardRadius,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(12.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                )
            }
        }
    }
}

/** The empty state of the saved screen. */
@Composable
private fun SavedEmptyState(onBrowseArchive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_heart_outline_24),
            contentDescription = null,
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.saved_empty_title),
            style = MohakashTextStyles.SectionHeading,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.saved_empty_body),
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.saved_browse_archive),
            onClick = onBrowseArchive,
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
        )
    }
}
