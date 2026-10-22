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
                        text = stringResource(R.string.saved_count, state.entries.size),
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
                        modifier = Modifier.padding(end = MohakashDimens.SidePadding),
                    )
                }
            },
        )

        if (state.entries.isEmpty() && !state.isLoading) {
            SavedEmptyState(onBrowseArchive = onBrowseArchive)
        } else {
            LazyVerticalGrid(
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
            contentDescription = entry.title,
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

/** The empty state of the saved screen. */
@Composable
private fun SavedEmptyState(onBrowseArchive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_heart_24),
                    contentDescription = null,
                    tint = MohakashColors.TextMuted,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(R.string.saved_empty_title),
                    style = MohakashTextStyles.SectionHeading,
                    color = MohakashColors.Text,
                )
                Text(
                    text = stringResource(R.string.saved_empty_body),
                    style = MohakashTextStyles.BodyMedium,
                    color = MohakashColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = stringResource(R.string.saved_browse_archive),
                    onClick = onBrowseArchive,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
