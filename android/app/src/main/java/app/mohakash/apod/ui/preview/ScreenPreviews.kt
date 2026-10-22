package app.mohakash.apod.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.mohakash.apod.data.storage.WallpaperMeta
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ArchiveIndexEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.archive.ArchiveContent
import app.mohakash.apod.ui.archive.ArchiveUiState
import app.mohakash.apod.ui.archive.FilterSheetContent
import app.mohakash.apod.ui.detail.DetailContent
import app.mohakash.apod.ui.detail.DetailUiState
import app.mohakash.apod.ui.onboarding.OnboardingContent
import app.mohakash.apod.ui.saved.SavedContent
import app.mohakash.apod.ui.saved.SavedUiState
import app.mohakash.apod.ui.settings.ClearCacheDialogContent
import app.mohakash.apod.ui.settings.SettingsContent
import app.mohakash.apod.ui.settings.SettingsUiState
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTheme
import app.mohakash.apod.ui.today.TodayContent
import app.mohakash.apod.ui.today.TodayUiState
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import app.mohakash.apod.ui.wallpaper.WallpaperSheetContent

/**
 * Editor previews for every screen and the important states.
 *
 * They are stateless: all of them are driven by a fake UiState, so they render without a
 * ViewModel, a database or a network connection.
 */

private fun previewEntry(
    date: String = "2026-10-23",
    title: String = "The Veil of Cygnus",
    mediaType: MediaType = MediaType.IMAGE,
    credit: String? = "Jane Doe",
): ApodEntry = ApodEntry(
    date = date,
    title = title,
    explanation = "A wide-field view of the Veil Nebula, the expanding remains of a star that " +
        "exploded thousands of years ago. The delicate filaments of ionised gas glow in " +
        "hydrogen and oxygen emission, tracing the shock fronts of the ancient supernova.\n\n" +
        "The nebula lies roughly 2,400 light-years away in the constellation Cygnus.",
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

private fun previewArchiveEntry(
    date: String,
    title: String,
    mediaType: MediaType = MediaType.IMAGE,
): ArchiveIndexEntry = ArchiveIndexEntry(
    date = date,
    title = title,
    mediaType = mediaType,
    thumbnailUrl = "https://example.com/thumb/$date.jpg",
    imageUrl = "https://example.com/image/$date.jpg",
    apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
    copyright = "Jane Doe",
    detailPath = "archive/$date.json",
)

private val previewSyncedAt: Long = System.currentTimeMillis() - 2L * 60L * 60L * 1000L

private val previewWallpaperMeta = WallpaperMeta(
    date = "2026-10-23",
    title = "The Veil of Cygnus",
    credit = "Jane Doe",
    sourceUrl = "https://example.com/apod/2026-10-23.jpg",
    fileName = "wallpaper.jpg",
    updatedAt = 0L,
)

private val previewArchiveState = ArchiveUiState(
    isLoading = false,
    entries = listOf(
        previewArchiveEntry("2026-10-23", "The Veil of Cygnus"),
        previewArchiveEntry("2026-10-22", "A Quiet Sea of Clouds", MediaType.VIDEO),
        previewArchiveEntry("2026-10-21", "The Ring of Fire"),
        previewArchiveEntry("2026-10-20", "Mosaic of the Moon"),
        previewArchiveEntry("2026-10-19", "Aurora over the Ridge"),
        previewArchiveEntry("2026-10-18", "The Open Cluster"),
    ),
    selectedYear = 2026,
    availableYears = listOf(2026, 2025, 2024, 2023, 2022, 2021),
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

@Composable
private fun TodayPreviewContent(
    state: TodayUiState,
    onOpenArchive: () -> Unit = {},
) {
    TodayContent(
        state = state,
        onRefresh = {},
        onOpenEntry = {},
        onOpenSettings = {},
        onToggleFavourite = {},
        onSaveImage = {},
        onShare = {},
        onRetry = {},
        onOpenArchive = onOpenArchive,
        onOpenWallpaper = {},
    )
}

@Preview(name = "Today · content", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayContentPreview() {
    PreviewSurface {
        TodayPreviewContent(
            TodayUiState(
                isLoading = false,
                entry = previewEntry(),
                syncedAt = previewSyncedAt,
                wallpaperMeta = null,
            ),
        )
    }
}

@Preview(name = "Today · skeleton", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodaySkeletonPreview() {
    PreviewSurface {
        TodayPreviewContent(TodayUiState(isLoading = true))
    }
}

@Preview(name = "Today · offline", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayOfflinePreview() {
    PreviewSurface {
        TodayPreviewContent(
            TodayUiState(
                isLoading = false,
                isOffline = true,
                entry = previewEntry(),
                syncedAt = previewSyncedAt,
                wallpaperMeta = previewWallpaperMeta,
            ),
        )
    }
}

@Preview(name = "Today · error", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayErrorPreview() {
    PreviewSurface {
        TodayPreviewContent(
            TodayUiState(isLoading = false, hasError = true),
            onOpenArchive = {},
        )
    }
}

@Preview(name = "Today · wallpaper active", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun TodayWallpaperActivePreview() {
    PreviewSurface {
        TodayPreviewContent(
            TodayUiState(
                isLoading = false,
                entry = previewEntry(),
                syncedAt = previewSyncedAt,
                wallpaperMeta = previewWallpaperMeta,
            ),
        )
    }
}

@Composable
private fun DetailPreviewContent(state: DetailUiState) {
    DetailContent(
        state = state,
        onBack = {},
        onToggleFavourite = {},
        onShare = {},
        onSaveImage = {},
        onOpenLink = {},
        onSetWallpaper = {},
        onRetry = {},
    )
}

@Preview(name = "Detail · image", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DetailPreview() {
    PreviewSurface {
        DetailPreviewContent(
            DetailUiState(
                isLoading = false,
                entry = previewEntry(),
                isFavourite = true,
                wallpaperMeta = previewWallpaperMeta,
            ),
        )
    }
}

@Preview(name = "Detail · video", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DetailVideoPreview() {
    PreviewSurface {
        DetailPreviewContent(
            DetailUiState(
                isLoading = false,
                entry = previewEntry(
                    date = "2026-10-22",
                    title = "A Quiet Sea of Clouds",
                    mediaType = MediaType.VIDEO,
                ),
            ),
        )
    }
}

@Preview(name = "Detail · loading", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun DetailLoadingPreview() {
    PreviewSurface {
        DetailPreviewContent(DetailUiState(isLoading = true))
    }
}

@Composable
private fun ArchivePreviewContent(state: ArchiveUiState) {
    ArchiveContent(
        state = state,
        onQueryChange = {},
        onSelectYear = {},
        onOpenFilters = {},
        onOpenEntry = {},
        onClearFilters = {},
        onLoadMore = {},
    )
}

@Preview(name = "Archive · content", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ArchivePreview() {
    PreviewSurface { ArchivePreviewContent(previewArchiveState) }
}

@Preview(name = "Archive · skeleton", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ArchiveSkeletonPreview() {
    PreviewSurface {
        ArchivePreviewContent(ArchiveUiState(isLoading = true, availableYears = listOf(2026, 2025, 2024)))
    }
}

@Preview(name = "Archive · empty", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ArchiveEmptyPreview() {
    PreviewSurface {
        ArchivePreviewContent(
            previewArchiveState.copy(
                entries = emptyList(),
                query = "nebula",
            ),
        )
    }
}

@Preview(name = "Saved · content", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SavedPreview() {
    PreviewSurface {
        SavedContent(
            state = SavedUiState(
                isLoading = false,
                entries = listOf(
                    previewEntry(),
                    previewEntry(date = "2026-10-22", title = "A Quiet Sea of Clouds"),
                    previewEntry(date = "2026-10-21", title = "The Ring of Fire"),
                ),
            ),
            onOpenEntry = {},
            onBrowseArchive = {},
        )
    }
}

@Preview(name = "Saved · empty", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SavedEmptyPreview() {
    PreviewSurface {
        SavedContent(
            state = SavedUiState(isLoading = false, entries = emptyList()),
            onOpenEntry = {},
            onBrowseArchive = {},
        )
    }
}

@Preview(name = "Settings", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SettingsPreview() {
    PreviewSurface {
        SettingsContent(
            state = SettingsUiState(
                updateAutomatically = true,
                preferHd = false,
                wifiOnly = true,
                scaling = WallpaperScaling.FILL,
                cacheSizeBytes = 84L * 1024L * 1024L,
                lastSyncAt = previewSyncedAt,
            ),
            onBack = {},
            onOpenLink = {},
            onUpdateAutomaticallyChange = {},
            onPreferHdChange = {},
            onWifiOnlyChange = {},
            onScalingChange = {},
            onClearCache = {},
        )
    }
}

@Preview(name = "Onboarding", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun OnboardingPreview() {
    PreviewSurface {
        OnboardingContent(
            preview = WallpaperPreview(),
            isPreparing = false,
            onSetWallpaper = {},
            onSkip = {},
        )
    }
}

@Preview(name = "Wallpaper sheet", widthDp = 390, heightDp = 700, showBackground = true)
@Composable
private fun WallpaperSheetPreview() {
    MohakashTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MohakashColors.Surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            WallpaperSheetContent(
                scaling = WallpaperScaling.FILL,
                updateDaily = true,
                preview = WallpaperPreview(),
                caption = "2026-10-23 · The Veil of Cygnus",
                onScalingChange = {},
                onUpdateDailyChange = {},
                onPreviewAndSet = {},
            )
        }
    }
}

@Preview(name = "Filter sheet", widthDp = 390, heightDp = 700, showBackground = true)
@Composable
private fun FilterSheetPreview() {
    MohakashTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MohakashColors.Surface),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FilterSheetContent(
                state = previewArchiveState,
                onYearSelected = {},
                onTypeSelected = {},
                onJumpToDate = {},
                onReset = {},
                onApply = {},
            )
        }
    }
}

@Preview(name = "Clear cache dialog", widthDp = 390, heightDp = 600, showBackground = true)
@Composable
private fun ClearCacheDialogPreview() {
    MohakashTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MohakashColors.Background),
            contentAlignment = Alignment.Center,
        ) {
            ClearCacheDialogContent(
                sizeBytes = 84L * 1024L * 1024L,
                onDismiss = {},
                onConfirm = {},
                modifier = Modifier.fillMaxSize().padding(MohakashDimens.SidePadding),
            )
        }
    }
}
