package app.mohakash.apod.ui.detail

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The loading status of the uncropped entry picture. */
private enum class EntryImageState { LOADING, SUCCESS, ERROR, UNAVAILABLE }

/**
 * The uncropped picture of an entry.
 *
 * The picture is always drawn in full (`ContentScale.Fit`): its box is as tall as the intrinsic
 * aspect ratio needs, capped at 62 % of the content viewport. Tapping twice zooms to 2.5×, a
 * pinch zooms up to 4×, dragging pans the zoomed picture and the clamp keeps its edges inside the
 * box. Single-finger drags on a non-zoomed picture are left to the screen scroll.
 *
 * Video and interactive entries show their poster with the play glyph and the *Video* chip; they
 * are played on the original APOD page instead of inside the app.
 *
 * @param viewportHeight the height of the scrollable content area, used for the max-height cap.
 * @param previewAspectRatio preview-only override so the IDE can render the portrait,
 *   landscape and panorama layouts without a loaded network picture.
 */
@Composable
internal fun EntryImageViewer(
    url: String?,
    contentDescription: String?,
    isVideo: Boolean,
    viewportHeight: Dp,
    modifier: Modifier = Modifier,
    previewAspectRatio: Float? = null,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingLabel = stringResource(R.string.entry_image_loading)
    val unavailableLabel = stringResource(R.string.detail_image_unavailable)
    val retryLabel = stringResource(R.string.entry_image_retry)
    val fallbackDescription = stringResource(R.string.entry_image_description)
    val videoChipLabel = stringResource(R.string.detail_video_chip)

    var retryKey by remember(url) { mutableIntStateOf(0) }
    val imageRequest = remember(url, retryKey) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(150)
            .build()
    }
    var imageState by remember(url, retryKey) {
        mutableStateOf(
            when {
                url.isNullOrBlank() -> EntryImageState.UNAVAILABLE
                else -> EntryImageState.LOADING
            },
        )
    }
    var aspectRatio by remember(url, previewAspectRatio) {
        mutableFloatStateOf(
            EntryImageSizing.sanitizeAspectRatio(
                previewAspectRatio ?: EntryImageSizing.DEFAULT_ASPECT_RATIO,
            ),
        )
    }
    val viewportHeightPx = with(density) { viewportHeight.toPx() }

    // Local zoom state: kept here so the hint row below the picture can reset it.
    var zoomScale by remember(url) { mutableFloatStateOf(EntryImageSizing.MIN_ZOOM) }
    var panOffset by remember(url) { mutableStateOf(Offset.Zero) }
    var zoomJob by remember(url) { mutableStateOf<Job?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val pictureBox = EntryImageSizing.imageBox(viewportWidthPx, viewportHeightPx, aspectRatio)
        val pictureBoxWidth = with(density) { pictureBox.width.toDp() }
        val pictureBoxHeight = with(density) { pictureBox.height.toDp() }
        val contentSize = EntryImageSizing.fitContent(
            boxWidth = pictureBox.width,
            boxHeight = pictureBox.height,
            aspectRatio = aspectRatio,
        )

        fun animateZoomTo(targetScale: Float) {
            zoomJob?.cancel()
            val startScale = zoomScale
            val startPan = panOffset
            val target = EntryImageSizing.clampScale(targetScale)
            zoomJob = scope.launch {
                animate(0f, 1f, animationSpec = tween(durationMillis = 220)) { fraction, _ ->
                    zoomScale = startScale + (target - startScale) * fraction
                    panOffset = Offset(startPan.x * (1f - fraction), startPan.y * (1f - fraction))
                }
            }
        }

        fun applyGesture(panChange: Offset, zoomChange: Float) {
            zoomJob?.cancel()
            val newScale = EntryImageSizing.clampScale(zoomScale * zoomChange)
            val bounds = EntryImageSizing.panBounds(
                boxWidth = pictureBox.width,
                boxHeight = pictureBox.height,
                content = contentSize,
                scale = newScale,
            )
            val nextPan = EntryImageSizing.clampPan(
                x = panOffset.x + panChange.x,
                y = panOffset.y + panChange.y,
                bounds = bounds,
            )
            zoomScale = newScale
            panOffset = Offset(nextPan.x, nextPan.y)
        }

        val zoomGestures = if (isVideo) {
            Modifier
        } else {
            Modifier
                .pointerInput(pictureBox.width, pictureBox.height, contentSize.width, contentSize.height) {
                    // Only pinch/pan consumes events: at 1× a single-finger drag must scroll the
                    // screen instead of being swallowed by the picture.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var event = awaitPointerEvent()
                        while (event.changes.any { it.pressed }) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val multiTouch = event.changes.count { it.pressed } > 1
                            val zoomed = EntryImageSizing.isZoomed(zoomScale)
                            if (zoomed || multiTouch) {
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    applyGesture(panChange, zoomChange)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            event = awaitPointerEvent()
                        }
                    }
                }
                .pointerInput(pictureBox.width, pictureBox.height) {
                    detectTapGestures(
                        onDoubleTap = {
                            animateZoomTo(EntryImageSizing.doubleTapScale(zoomScale))
                        },
                    )
                }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pictureBoxHeight)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(pictureBoxWidth)
                        .height(pictureBoxHeight)
                        .clip(RoundedCornerShape(MohakashDimens.CardRadius))
                        .clipToBounds()
                        .background(MohakashColors.Background),
                ) {
                when {
                    url.isNullOrBlank() -> EntryImageUnavailable(
                        label = if (isVideo) null else unavailableLabel,
                        modifier = Modifier.fillMaxSize(),
                    )

                    imageState == EntryImageState.ERROR -> EntryImageError(
                        label = unavailableLabel,
                        retryLabel = retryLabel,
                        onRetry = { retryKey++ },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = contentDescription
                                ?.takeIf { it.isNotBlank() }
                                ?: fallbackDescription,
                            contentScale = ContentScale.Fit,
                            onLoading = { imageState = EntryImageState.LOADING },
                            onSuccess = { success ->
                                val drawable = success.result.drawable
                                val width = drawable.intrinsicWidth
                                val height = drawable.intrinsicHeight
                                if (previewAspectRatio == null && width > 0 && height > 0) {
                                    aspectRatio = EntryImageSizing.sanitizeAspectRatio(
                                        width.toFloat() / height.toFloat(),
                                    )
                                }
                                imageState = EntryImageState.SUCCESS
                            },
                            onError = { imageState = EntryImageState.ERROR },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(zoomGestures)
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                    // The zoomed picture must never draw over the title or the bar.
                                    clip = true
                                },
                        )
                        if (imageState == EntryImageState.LOADING) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .busySemantics(loadingLabel),
                                cornerRadius = 0.dp,
                            )
                        }
                    }
                }

                if (isVideo) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_24),
                        contentDescription = null,
                        tint = MohakashColors.TextMuted,
                        modifier = Modifier.size(56.dp),
                    )
                    if (!url.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MohakashColors.Background)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = videoChipLabel,
                                style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold),
                                color = MohakashColors.Text,
                            )
                        }
                    }
                }
                }
            }

        }
    }
}

/** The label shown instead of a picture that cannot be loaded, with an optional retry action. */
@Composable
private fun EntryImageError(
    label: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MohakashTextStyles.Helper,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        TextAction(
            text = retryLabel,
            onClick = onRetry,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The fallback of an entry without a picture: a muted label for a missing image. Video entries
 * have no label here — the play glyph drawn over every poster is enough.
 */
@Composable
private fun EntryImageUnavailable(
    label: String?,
    modifier: Modifier = Modifier,
) {
    if (label == null) return
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MohakashTextStyles.Helper,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
