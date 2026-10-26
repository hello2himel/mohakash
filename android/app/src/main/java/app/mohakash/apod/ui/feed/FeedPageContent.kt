package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.mohakash.apod.R
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SkeletonBlock
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.busySemantics
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The overlay chrome is deliberately solid, not a gradient: contrast over a bright photograph
 * comes from a translucent panel and a hairline, never from a blurred pseudo-glass effect.
 */
private const val OVERLAY_ALPHA = 0.97f
private const val GLASS_ALPHA = 0.72f

/** Everything the feed page needs from the screen. Grouped so the page stays a small function. */
internal data class FeedPageActions(
    val onOpenEntry: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onToggleFavourite: (String) -> Unit = {},
    val onSaveImage: (String) -> Unit = {},
    val onShare: (String) -> Unit = {},
    val onOpenWallpaper: (String) -> Unit = {},
    val onRetryHydration: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onClearFilters: () -> Unit = {},
)

/**
 * One full-screen APOD page: image (or poster), top chrome and the bottom overlay.
 *
 * The image is `ContentScale.Crop` when the user picked *Fill* and `ContentScale.Fit` (letterboxed
 * on the app background) when they picked *Fit*. That is the one `WallpaperScaling` setting — the
 * feed and the live wallpaper cannot disagree.
 */
@Composable
internal fun FeedPageContent(
    page: FeedPageUi,
    isFavourite: Boolean,
    wallpaperActive: Boolean,
    scaling: WallpaperScaling,
    isOffline: Boolean,
    syncedAt: Long?,
    now: Long,
    filters: FeedFilters,
    actions: FeedPageActions,
    modifier: Modifier = Modifier,
) {
    val date = page.date ?: return
    val entry = page.entry
    val indexEntry = page.indexEntry
    val mediaType = entry?.mediaType ?: indexEntry?.mediaType ?: MediaType.OTHER
    val isVideo = mediaType.isPlayable
    val title = entry?.title ?: indexEntry?.title.orEmpty()
    val credit = entry?.displayCredit ?: indexEntry?.displayCredit
    val teaser = entry?.explanation?.takeIf { it.isNotBlank() }
    val hydrationFailed = page.hydration == Hydration.FAILED

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        val overlayMaxHeight = maxHeight * 0.62f
        FeedImageLayer(
            page = page,
            scaling = scaling,
            isVideo = isVideo,
            contentDescription = entry?.altText ?: title.ifBlank { null },
        )

        if (isVideo) {
            VideoBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = MohakashDimens.SidePadding, top = 64.dp),
            )
        }

        // ---- Top chrome -----------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MohakashDimens.SidePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chipLabel = topChipLabel(
                    isLatest = page.isLatest,
                    date = date,
                    isOffline = isOffline,
                    syncedAt = syncedAt,
                    now = now,
                )
                GlassPill(
                    text = chipLabel,
                    onClick = actions.onRefresh,
                    contentDescription = stringResource(R.string.feed_refresh_latest),
                    leadingDot = isOffline,
                )
                Spacer(Modifier.weight(1f))
                FeedGlassIconButton(
                    painter = painterResource(R.drawable.ic_search_24),
                    contentDescription = stringResource(R.string.feed_search),
                    onClick = actions.onOpenSearch,
                )
                Spacer(Modifier.width(8.dp))
                FeedGlassIconButton(
                    painter = painterResource(R.drawable.ic_tune_24),
                    contentDescription = stringResource(R.string.feed_filters),
                    onClick = actions.onOpenFilters,
                    showDot = filters.isActive,
                )
                Spacer(Modifier.width(8.dp))
                FeedGlassIconButton(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = stringResource(R.string.feed_settings),
                    onClick = actions.onOpenSettings,
                )
            }
            if (filters.isActive) {
                ActiveFilterChip(
                    filters = filters,
                    onClick = actions.onClearFilters,
                )
            }
        }

        // ---- Bottom overlay --------------------------------------------------------------
        // Solid translucent panel with a 1 dp hairline at its top edge. Deliberately not a
        // gradient: the photograph stays the only "effect" on screen.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = overlayMaxHeight)
                .clip(
                    RoundedCornerShape(
                        topStart = MohakashDimens.FeedOverlayRadius,
                        topEnd = MohakashDimens.FeedOverlayRadius,
                    ),
                )
                .background(MohakashColors.Background.copy(alpha = OVERLAY_ALPHA)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Border),
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = MohakashDimens.SidePadding)
                    .padding(top = 14.dp, bottom = 12.dp),
            ) {
                Text(
                    text = feedCreditLine(date, credit),
                    style = MohakashTextStyles.Caption,
                    color = MohakashColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title.ifBlank { stringResource(R.string.detail_image_unavailable) },
                    style = MohakashTextStyles.PageTitle,
                    color = MohakashColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (teaser != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = teaser,
                        style = MohakashTextStyles.BodyBase,
                        color = MohakashColors.TextPreview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (isOffline) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.feed_explanation_unavailable),
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
                    )
                }

                if (hydrationFailed) {
                    Spacer(Modifier.height(8.dp))
                    HydrationFailureRow(onRetry = { actions.onRetryHydration(date) })
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FeedActionIcon(
                        painter = painterResource(
                            if (isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                        ),
                        contentDescription = stringResource(R.string.feed_save),
                        onClick = { actions.onToggleFavourite(date) },
                        tint = if (isFavourite) MohakashColors.Accent else MohakashColors.Text,
                        enabled = entry != null,
                    )
                    FeedActionIcon(
                        painter = painterResource(R.drawable.ic_share_24),
                        contentDescription = stringResource(R.string.feed_share),
                        onClick = { actions.onShare(date) },
                        tint = MohakashColors.Text,
                    )
                    if (!isVideo && !isOffline) {
                        FeedActionIcon(
                            painter = painterResource(R.drawable.ic_download_24),
                            contentDescription = stringResource(R.string.feed_download),
                            onClick = { actions.onSaveImage(date) },
                            tint = MohakashColors.Text,
                            enabled = entry != null,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (isVideo) {
                        WatchOnApodPill(
                            onClick = {
                                val url = entry?.apodUrl ?: indexEntry?.apodUrl
                                if (!url.isNullOrBlank()) actions.onOpenLink(url) else actions.onOpenEntry(date)
                            },
                        )
                    } else if (!isOffline) {
                        WallpaperPill(
                            active = wallpaperActive,
                            enabled = entry != null,
                            onClick = { actions.onOpenWallpaper(date) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                FullEntryButton(onClick = { actions.onOpenEntry(date) })

                if (isOffline) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.feed_offline_chip),
                            style = MohakashTextStyles.Caption,
                            color = MohakashColors.TextMuted,
                            modifier = Modifier.weight(1f),
                        )
                        TextAction(
                            text = stringResource(R.string.feed_retry),
                            onClick = actions.onRefresh,
                        )
                    }
                }
            }
        }
    }
}

/** The image / poster layer that fills the whole page behind the overlays. */
@Composable
private fun FeedImageLayer(
    page: FeedPageUi,
    scaling: WallpaperScaling,
    isVideo: Boolean,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val entry = page.entry
    val indexEntry = page.indexEntry
    val fullUrl = if (isVideo) {
        entry?.thumbnailUrl ?: entry?.displayImageUrl ?: indexEntry?.thumbnailUrl ?: indexEntry?.imageUrl
    } else {
        entry?.displayImageUrl ?: indexEntry?.thumbnailUrl ?: indexEntry?.imageUrl
    }
    val thumbnailUrl = if (isVideo) {
        indexEntry?.thumbnailUrl
    } else {
        entry?.thumbnailUrl ?: indexEntry?.thumbnailUrl
    }
    val unusableImage = !isVideo && entry != null && !entry.imageStatus.isUsable

    var failed by remember(fullUrl, thumbnailUrl) { mutableStateOf(false) }
    var useFallback by remember(fullUrl, thumbnailUrl) { mutableStateOf(false) }
    LaunchedEffect(fullUrl, thumbnailUrl) {
        failed = false
        useFallback = false
    }

    val url = when {
        failed -> null
        useFallback -> thumbnailUrl
        else -> fullUrl ?: thumbnailUrl
    }
    val model = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context).data(it).crossfade(150).build()
        }
    }
    val scale = if (scaling == WallpaperScaling.FILL) ContentScale.Crop else ContentScale.Fit

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        val hasPicture = model != null && !unusableImage
        if (hasPicture) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (!useFallback && thumbnailUrl != null && thumbnailUrl != url) {
                        useFallback = true
                    } else {
                        failed = true
                    }
                },
                onSuccess = { failed = false },
            )
        }
        if (isVideo && hasPicture) {
            Icon(
                painter = painterResource(R.drawable.ic_play_24),
                contentDescription = stringResource(R.string.detail_video_chip),
                tint = MohakashColors.Text.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp),
            )
        }
        if (!hasPicture) {
            // No picture at all (old video without a poster, a missing/placeholder image): a calm
            // typographic card instead of an empty black rectangle.
            TypographicFallback(
                title = page.entry?.title ?: page.indexEntry?.title,
                showUnavailableLabel = !isVideo,
            )
        }
    }
}

/** A calm typographic card used when an entry has no picture at all. */
@Composable
private fun TypographicFallback(title: String?, showUnavailableLabel: Boolean = true) {
    Column(
        modifier = Modifier.padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showUnavailableLabel) {
            Text(
                text = stringResource(R.string.detail_image_unavailable),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MohakashTextStyles.SheetTitle,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The small "Video" chip shown on a poster. */
@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MohakashColors.Background.copy(alpha = GLASS_ALPHA))
            .border(1.dp, MohakashColors.BorderStrong, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_video_chip),
            style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold),
            color = MohakashColors.Text,
        )
    }
}

/**
 * The tappable status chip on the left of the top row.
 *
 * Page 0 reads *Latest · synced 2 h ago*; other pages read their year. The chip is also the
 * always-available refresh affordance if the pull gesture is ever unreliable.
 */
@Composable
private fun GlassPill(
    text: String,
    onClick: () -> Unit,
    contentDescription: String?,
    leadingDot: Boolean,
) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(MohakashColors.Surface.copy(alpha = GLASS_ALPHA))
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .clickable(
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingDot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MohakashColors.TextMuted),
            )
        }
        Text(
            text = text,
            style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.Medium),
            color = MohakashColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 44 dp glass circle — the icon buttons of the overlay chrome. */
@Composable
private fun FeedGlassIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    showDot: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(MohakashDimens.FeedGlassButton)
            .clip(CircleShape)
            .background(MohakashColors.Surface.copy(alpha = GLASS_ALPHA))
            .border(1.dp, MohakashColors.BorderStrong, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = MohakashColors.Text,
            modifier = Modifier.size(22.dp),
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MohakashColors.Accent),
            )
        }
    }
}

/** The compact active-filter chip under the top row; tapping clears the filters. */
@Composable
private fun ActiveFilterChip(filters: FeedFilters, onClick: () -> Unit) {
    val yearLabel = filters.year?.toString() ?: stringResource(R.string.filter_all_years)
    val mediaLabel = when (filters.media) {
        FeedMediaFilter.ALL -> stringResource(R.string.filter_all)
        FeedMediaFilter.IMAGES -> stringResource(R.string.filter_images)
        FeedMediaFilter.VIDEOS -> stringResource(R.string.filter_videos)
    }
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(MohakashColors.Surface.copy(alpha = GLASS_ALPHA))
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_filters_active, yearLabel, mediaLabel),
            style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.Medium),
            color = MohakashColors.Text,
        )
        Icon(
            painter = painterResource(R.drawable.ic_close_24),
            contentDescription = stringResource(R.string.feed_clear_filters),
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A 48 dp action icon that can be disabled while its page is still loading. */
@Composable
private fun FeedActionIcon(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
) {
    IconActionButton(
        painter = painter,
        contentDescription = contentDescription,
        onClick = { if (enabled) onClick() },
        tint = if (enabled) tint else tint.copy(alpha = 0.45f),
        modifier = if (enabled) Modifier else Modifier.semantics { disabled() },
    )
}

/** The "Wallpaper" / "Wallpaper active" pill of the action row. */
@Composable
private fun WallpaperPill(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Box(
        modifier = Modifier
            .heightIn(min = MohakashDimens.TouchTarget)
            .clip(shape)
            .then(
                if (active) {
                    Modifier.border(1.dp, MohakashColors.BorderStrong, shape)
                } else {
                    Modifier.background(if (enabled) MohakashColors.Accent else MohakashColors.SurfaceStrong)
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (active) R.string.feed_wallpaper_active else R.string.feed_set_wallpaper,
            ),
            style = MohakashTextStyles.UiSmall.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                active -> MohakashColors.Text
                enabled -> MohakashColors.OnAccent
                else -> MohakashColors.TextMuted
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** The video-only action: open the original APOD page (no inline playback in 1.0.5). */
@Composable
private fun WatchOnApodPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(MohakashDimens.PillRadius)
    Row(
        modifier = Modifier
            .heightIn(min = MohakashDimens.TouchTarget)
            .clip(shape)
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_external_24),
            contentDescription = null,
            tint = MohakashColors.Text,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.feed_watch_on_apod),
            style = MohakashTextStyles.UiSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MohakashColors.Text,
        )
    }
}

/** The full-width outlined button that opens the uncropped detail viewer. */
@Composable
private fun FullEntryButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(MohakashDimens.ButtonRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_full_entry),
            style = MohakashTextStyles.UiSmallSemiBold,
            color = MohakashColors.Text,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_24),
            contentDescription = null,
            tint = MohakashColors.TextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Inline retry for a page whose full record could not be loaded. */
@Composable
private fun HydrationFailureRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_hydration_failed),
            style = MohakashTextStyles.Caption,
            color = MohakashColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        TextAction(text = stringResource(R.string.feed_retry), onClick = onRetry)
    }
}

/** The chip text: *Latest · synced …* on page 0, the year elsewhere. */
@Composable
private fun topChipLabel(
    isLatest: Boolean,
    date: String,
    isOffline: Boolean,
    syncedAt: Long?,
    now: Long,
): String {
    if (isOffline) return stringResource(R.string.feed_offline_chip)
    if (!isLatest) return date.take(4)
    if (syncedAt == null) return stringResource(R.string.feed_latest)
    val synced = stringResource(R.string.feed_latest_synced, TimeFormatting.formatSyncedAgo(syncedAt, now))
    return synced
}

/**
 * A calm skeleton that matches the loaded page layout: the image block and the overlay block keep
 * their final size, so nothing jumps when the first page arrives.
 */
@Composable
internal fun FeedSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.feed_loading)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .busySemantics(loadingLabel),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkeletonBlock(
                Modifier
                    .width(180.dp)
                    .height(12.dp),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(28.dp),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp),
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    SkeletonBlock(
                        Modifier
                            .size(48.dp),
                        cornerRadius = 24.dp,
                    )
                }
            }
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                cornerRadius = MohakashDimens.ButtonRadius,
            )
        }
    }
}

/** The end-of-archive card — a real last page, never a wrap-around. */
@Composable
internal fun FeedEndCard(
    filters: FeedFilters,
    isIndexComplete: Boolean,
    isOffline: Boolean,
    onBackToLatest: () -> Unit,
    onOpenSaved: () -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFilteredEnd = filters.isActive
    val endTitle = when {
        isFilteredEnd -> stringResource(R.string.feed_end_filtered)
        !isIndexComplete && !isOffline -> stringResource(R.string.feed_loading_older)
        else -> stringResource(R.string.feed_end_title)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = endTitle,
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
        )
        val endBody = when {
            isFilteredEnd -> null
            !isIndexComplete && isOffline -> stringResource(R.string.feed_end_offline)
            isIndexComplete -> stringResource(R.string.feed_end_body)
            else -> null
        }
        if (endBody != null) {
            Text(
                text = endBody,
                style = MohakashTextStyles.BodyBase,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!isIndexComplete) {
            if (!isOffline) {
                CircularProgressIndicator(
                    color = MohakashColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .size(20.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.feed_end_retry),
                    style = MohakashTextStyles.Caption,
                    color = MohakashColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                SecondaryButton(
                    text = stringResource(R.string.feed_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (isFilteredEnd) {
            PrimaryButton(
                text = stringResource(R.string.feed_clear_filters),
                onClick = onClearFilters,
                modifier = Modifier.padding(top = 20.dp),
            )
            SecondaryButton(
                text = stringResource(R.string.feed_back_to_latest),
                onClick = onBackToLatest,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.feed_back_to_latest),
                onClick = onBackToLatest,
                modifier = Modifier.padding(top = 20.dp),
            )
            SecondaryButton(
                text = stringResource(R.string.feed_open_saved),
                onClick = onOpenSaved,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The full-page error state: no data at all (first run, offline). */
@Composable
internal fun FeedFatalError(
    onRetry: () -> Unit,
    onOpenSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_error_title),
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feed_error_body),
            style = MohakashTextStyles.BodyBase,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            text = stringResource(R.string.feed_error_try_again),
            onClick = onRetry,
            modifier = Modifier.padding(top = 20.dp),
        )
        SecondaryButton(
            text = stringResource(R.string.feed_open_saved),
            onClick = onOpenSaved,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
