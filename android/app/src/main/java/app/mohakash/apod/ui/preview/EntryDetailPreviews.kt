package app.mohakash.apod.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.ImageStatus
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.ui.detail.DetailContent
import app.mohakash.apod.ui.detail.DetailUiState
import app.mohakash.apod.ui.detail.EntryZoomHintRow
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTheme

/**
 * Editor previews of the rebuilt entry viewer.
 *
 * The network picture itself is not loaded in the IDE preview, so the image previews show the
 * loading skeleton at the aspect ratio the layout would use. The stateless [DetailContent] keeps
 * every other state (video, loading, error, offline, favourite) honest without a ViewModel.
 */

private fun previewEntry(
    date: String = "2026-10-26",
    title: String = "M42: The Great Nebula in Orion",
    mediaType: MediaType = MediaType.IMAGE,
    imageStatus: ImageStatus = ImageStatus.OK,
    credit: String? = "Roberto Palaia",
    updatedAt: String? = "2026-10-26T06:00:00Z",
    source: String? = "apod",
    explanation: String = PREVIEW_EXPLANATION,
): ApodEntry = ApodEntry(
    date = date,
    title = title,
    explanation = explanation,
    mediaType = mediaType,
    imageUrl = if (mediaType == MediaType.IMAGE) "https://example.com/apod/$date.jpg" else null,
    hdImageUrl = if (mediaType == MediaType.IMAGE) "https://example.com/apod/$date-hd.jpg" else null,
    thumbnailUrl = "https://example.com/apod/$date-thumb.jpg",
    apodUrl = "https://apod.nasa.gov/apod/ap$date.html",
    copyright = credit,
    credit = credit,
    altText = "A glowing nebula in deep space",
    source = source,
    sourceId = "apod-$date",
    sourceMediaType = mediaType.name.lowercase(),
    imageStatus = imageStatus,
    updatedAt = updatedAt,
    lastSuccessfulSync = "2026-10-26T06:05:00Z",
    latestAvailableDate = date,
    recordCount = 1,
    sourceStatus = "ok",
)

private const val PREVIEW_EXPLANATION =
    "The Great Nebula in Orion is a familiar sight to amateur astronomers: a vast stellar " +
        "nursery about 1,500 light-years away where new stars are being born.\n\n" +
        "The delicate pink and blue filaments are clouds of hydrogen and oxygen lit by the " +
        "young stars embedded inside them. The bright centre is the Trapezium cluster."

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
private fun EntryPreviewContent(
    state: DetailUiState,
    imagePreviewAspectRatio: Float? = null,
) {
    DetailContent(
        state = state,
        onBack = {},
        onToggleFavourite = {},
        onShare = {},
        onSaveImage = {},
        onOpenLink = {},
        onOpenSettings = {},
        onSetWallpaper = {},
        onRetry = {},
        imagePreviewAspectRatio = imagePreviewAspectRatio,
    )
}

@Preview(name = "Entry · landscape image", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryLandscapePreview() {
    PreviewSurface {
        EntryPreviewContent(
            state = DetailUiState(isLoading = false, entry = previewEntry()),
            imagePreviewAspectRatio = 3f / 2f,
        )
    }
}

@Preview(name = "Entry · portrait image", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryPortraitPreview() {
    PreviewSurface {
        EntryPreviewContent(
            state = DetailUiState(isLoading = false, entry = previewEntry()),
            imagePreviewAspectRatio = 2f / 3f,
        )
    }
}

@Preview(name = "Entry · panorama", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryPanoramaPreview() {
    PreviewSurface {
        EntryPreviewContent(
            state = DetailUiState(isLoading = false, entry = previewEntry()),
            imagePreviewAspectRatio = 4f,
        )
    }
}

@Preview(name = "Entry · video", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryVideoPreview() {
    PreviewSurface {
        EntryPreviewContent(
            state = DetailUiState(
                isLoading = false,
                entry = previewEntry(
                    date = "2026-10-25",
                    title = "A Quiet Sea of Clouds",
                    mediaType = MediaType.VIDEO,
                ),
            ),
        )
    }
}

@Preview(name = "Entry · favourite offline", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryOfflineFavouritePreview() {
    PreviewSurface {
        EntryPreviewContent(
            state = DetailUiState(
                isLoading = false,
                entry = previewEntry(),
                isFavourite = true,
                isShowingCachedCopy = true,
            ),
        )
    }
}

@Preview(name = "Entry · loading", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryLoadingPreview() {
    PreviewSurface {
        EntryPreviewContent(state = DetailUiState(isLoading = true))
    }
}

@Preview(name = "Entry · error", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun EntryErrorPreview() {
    PreviewSurface {
        EntryPreviewContent(state = DetailUiState(isLoading = false, hasError = true))
    }
}

@Preview(name = "Entry · zoom hint", widthDp = 390, heightDp = 120, showBackground = true)
@Composable
private fun EntryZoomHintPreview() {
    PreviewSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            EntryZoomHintRow(zoomed = true, onReset = {})
        }
    }
}
