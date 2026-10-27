package app.mohakash.apod.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.onboarding.OnboardingContent
import app.mohakash.apod.ui.onboarding.OnboardingFrequencyContent
import app.mohakash.apod.ui.saved.SavedContent
import app.mohakash.apod.ui.saved.SavedUiState
import app.mohakash.apod.ui.settings.AdvancedContent
import app.mohakash.apod.ui.settings.AdvancedUiState
import app.mohakash.apod.ui.settings.ClearCacheDialogContent
import app.mohakash.apod.ui.settings.SettingsContent
import app.mohakash.apod.ui.settings.SettingsUiState
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTheme
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import app.mohakash.apod.ui.wallpaper.WallpaperSheetContent

/**
 * Editor previews for the screens that are not part of the feed itself.
 *
 * The feed (pages, sheets, states) lives in `ui/feed/FeedPreviews.kt`; the rebuilt entry viewer
 * lives in `ui/preview/EntryDetailPreviews.kt`. Keeping the three files apart means the builders
 * never edit the same preview file.
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
        "exploded thousands of years ago.\n\n" +
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

private val previewSyncedAt: Long = System.currentTimeMillis() - 2L * 60L * 60L * 1000L

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
            onBrowseFeed = {},
            onOpenSettings = {},
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
            onBrowseFeed = {},
            onOpenSettings = {},
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
                wifiOnly = false,
                scaling = WallpaperScaling.FILL,
                cacheSizeBytes = 84L * 1024L * 1024L,
                lastSyncAt = previewSyncedAt,
            ),
            onBack = {},
            onOpenLink = {},
            onOpenAdvanced = {},
            onUpdateAutomaticallyChange = {},
            onNotificationsEnabledChange = {},
            onDonationRemindersChange = {},
            onCarouselModeChange = {},
            onPreferHdChange = {},
            onWifiOnlyChange = {},
            onHideVideosChange = {},
            onScalingChange = {},
            onClearCache = {},
        )
    }
}

@Preview(name = "Advanced settings", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AdvancedSettingsPreview() {
    PreviewSurface {
        AdvancedContent(
            state = AdvancedUiState(
                isLoading = false,
                input = "https://example.org/api/v1",
                savedCustomUrl = "https://example.org/api/v1",
                defaultUrl = "https://hello2himel.github.io/mohakash/api/v1",
            ),
            onBack = {},
            onInputChange = {},
            onSave = {},
            onReset = {},
            onOpenLink = {},
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

@Preview(name = "Onboarding · frequency", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun OnboardingFrequencyPreview() {
    PreviewSurface {
        OnboardingFrequencyContent(onContinue = {})
    }
}

@Preview(
    name = "Onboarding · frequency · 320dp at 2x",
    widthDp = 320,
    heightDp = 640,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun OnboardingFrequencyNarrowPreview() {
    PreviewSurface {
        OnboardingFrequencyContent(onContinue = {})
    }
}

@Preview(
    name = "Onboarding · frequency · landscape at 2x",
    widthDp = 480,
    heightDp = 320,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun OnboardingFrequencyLandscapePreview() {
    PreviewSurface {
        OnboardingFrequencyContent(onContinue = {})
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MohakashDimens.SidePadding),
            )
        }
    }
}
