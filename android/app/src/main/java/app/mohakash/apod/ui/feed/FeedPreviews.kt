package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTheme

/**
 * Editor previews of the feed. They are stateless: every preview is driven by a fake UiState, so
 * they render without a ViewModel, a database or a network connection.
 */

private fun previewEntry(
    date: String = "2026-10-23",
    title: String = "The Veil of Cygnus",
    mediaType: MediaType = MediaType.IMAGE,
    credit: String? = "Roberto Palaia",
): ApodEntry = ApodEntry(
    date = date,
    title = title,
    explanation = "A wide-field view of the Veil Nebula, the expanding remains of a star that " +
        "exploded thousands of years ago. The delicate filaments of ionised gas glow in " +
        "hydrogen and oxygen emission, tracing the shock fronts of the ancient supernova.",
    mediaType = mediaType,
    imageUrl = "https://example.com/apod/$date.jpg",
    hdImageUrl = "https://example.com/apod/$date-hd.jpg",
    thumbnailUrl = "https://example.com/apod/$date-thumb.jpg",
    apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
    copyright = credit,
    credit = credit,
    altText = "A glowing arc of gas in deep space",
    source = "apod",
    sourceId = "apod-$date",
    sourceMediaType = mediaType.name.lowercase(),
    imageStatus = ImageStatus.OK,
    updatedAt = "2026-10-23T06:00:00Z",
    lastSuccessfulSync = "2026-10-23T06:05:00Z",
    latestAvailableDate = date,
    recordCount = 1,
    sourceStatus = "ok",
)

private fun previewIndex(
    date: String = "2026-10-23",
    title: String = "The Veil of Cygnus",
    mediaType: MediaType = MediaType.IMAGE,
): ArchiveIndexEntry = ArchiveIndexEntry(
    date = date,
    title = title,
    mediaType = mediaType,
    thumbnailUrl = "https://example.com/apod/$date-thumb.jpg",
    imageUrl = null,
    apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
    copyright = "Roberto Palaia",
    detailPath = "archive/$date.json",
)

private fun previewPage(
    entry: ApodEntry?,
    indexEntry: ArchiveIndexEntry? = null,
    hydration: Hydration = if (entry != null) Hydration.READY else Hydration.LOADING,
    isLatest: Boolean = true,
): FeedPageUi {
    val date = entry?.date ?: indexEntry!!.date
    val resolvedIndex = indexEntry ?: with(FeedPaging) { entry?.toIndexEntry() }
    return FeedPageUi(
        page = FeedPage.Entry(date),
        indexEntry = resolvedIndex,
        entry = entry,
        hydration = hydration,
        isLatest = isLatest,
    )
}

private fun previewState(
    pages: List<FeedPageUi>,
    isOffline: Boolean = false,
    filters: FeedFilters = FeedFilters(),
    scaling: WallpaperScaling = WallpaperScaling.FILL,
): FeedUiState = FeedUiState(
    pages = pages,
    allItems = pages.mapNotNull { page -> page.date?.let { FeedDateItem(it, page.indexEntry) } },
    filters = filters,
    availableYears = listOf(2026, 2025, 2024, 2023, 2022, 2021),
    isLoadingLatest = false,
    isIndexComplete = true,
    isOffline = isOffline,
    scaling = scaling,
    currentPageKey = pages.firstOrNull()?.page?.key,
    archiveFirstDate = "1995-06-16",
    archiveLastDate = "2026-10-23",
)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MohakashTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MohakashColors.Background),
        ) {
            content()
        }
    }
}

@Preview(name = "Feed · page", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedPagePreview() {
    val entry = previewEntry()
    PreviewSurface {
        FeedPageContent(
            page = previewPage(entry),
            isFavourite = true,
            wallpaperActive = false,
            scaling = WallpaperScaling.FILL,
            isOffline = false,
            filters = FeedFilters(),
            actions = FeedPageActions(),
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}

@Preview(name = "Feed · video", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedVideoPreview() {
    val entry = previewEntry(date = "2026-10-22", title = "A Quiet Sea of Clouds", mediaType = MediaType.VIDEO)
    PreviewSurface {
        FeedPageContent(
            page = previewPage(entry),
            isFavourite = false,
            wallpaperActive = false,
            scaling = WallpaperScaling.FILL,
            isOffline = false,
            filters = FeedFilters(),
            actions = FeedPageActions(),
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}

@Preview(name = "Feed · fit + active filter", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedFitFilterPreview() {
    val entry = previewEntry()
    PreviewSurface {
        FeedPageContent(
            page = previewPage(entry),
            isFavourite = false,
            wallpaperActive = true,
            scaling = WallpaperScaling.FIT,
            isOffline = false,
            filters = FeedFilters(year = 2024, media = FeedMediaFilter.IMAGES),
            actions = FeedPageActions(),
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}

@Preview(name = "Feed · offline", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedOfflinePreview() {
    val entry = previewEntry()
    PreviewSurface {
        FeedPageContent(
            page = previewPage(entry, hydration = Hydration.IDLE),
            isFavourite = false,
            wallpaperActive = false,
            scaling = WallpaperScaling.FILL,
            isOffline = true,
            filters = FeedFilters(),
            actions = FeedPageActions(),
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}

@Preview(name = "Feed · hydrated failure", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedHydrationFailurePreview() {
    PreviewSurface {
        FeedPageContent(
            page = previewPage(
                entry = null,
                indexEntry = previewIndex(),
                hydration = Hydration.FAILED,
            ),
            isFavourite = false,
            wallpaperActive = false,
            scaling = WallpaperScaling.FILL,
            isOffline = false,
            filters = FeedFilters(),
            actions = FeedPageActions(),
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}

@Preview(name = "Feed · loading", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedLoadingPreview() {
    PreviewSurface { FeedSkeleton() }
}

@Preview(name = "Feed · end", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedEndPreview() {
    PreviewSurface {
        FeedEndCard(
            filters = FeedFilters(),
            isIndexComplete = true,
            isOffline = false,
            onBackToLatest = {},
            onOpenSaved = {},
            onClearFilters = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Feed · end filtered", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedEndFilteredPreview() {
    PreviewSurface {
        FeedEndCard(
            filters = FeedFilters(year = 2024, media = FeedMediaFilter.IMAGES),
            isIndexComplete = true,
            isOffline = false,
            onBackToLatest = {},
            onOpenSaved = {},
            onClearFilters = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Feed · fatal error", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedFatalPreview() {
    PreviewSurface { FeedFatalError(onRetry = {}, onOpenSaved = {}) }
}

@Preview(name = "Feed · search sheet", widthDp = 390, heightDp = 780, showBackground = true)
@Composable
private fun FeedSearchPreview() {
    val entry = previewEntry()
    val index = previewIndex()
    PreviewSurface {
        FeedSearchSheetContent(
            state = FeedSearchState(
                query = "nebula",
                results = listOf(FeedDateItem(entry.date, index)),
            ),
            availableYears = listOf(2026, 2025, 2024),
            onQueryChange = {},
            onFiltersChange = {},
            onSelectResult = {},
            onGoToDate = {},
            onDismiss = {},
            onRetryIndex = {},
        )
    }
}

@Preview(name = "Feed · full state", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun FeedContentPreview() {
    val entry = previewEntry()
    PreviewSurface {
        FeedContent(
            state = previewState(
                pages = listOf(previewPage(entry), FeedPageUi(page = FeedPage.End)),
            ),
            actions = FeedPageActions(),
            wallpaperActiveFor = { false },
            onPageSettled = {},
            onConsumeJump = {},
            onBackToLatest = {},
            onOpenSaved = {},
            onRetryAll = {},
            showSwipeHint = false,
            onDismissSwipeHint = {},
            chromeVisible = true,
            onToggleChrome = {},
        )
    }
}
