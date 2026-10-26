package app.mohakash.apod.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.domain.model.WallpaperScaling
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
private const val GLASS_ALPHA = 0.72f

/** Everything the feed page needs from the screen. Grouped so the page stays a small function. */
internal data class FeedPageActions(
    val onOpenEntry: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenSearch: () -> Unit = {},
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
    filters: FeedFilters,
    actions: FeedPageActions,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    bottomBarInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val date = page.date ?: return
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val entry = page.entry
    val indexEntry = page.indexEntry
    val mediaType = entry?.mediaType ?: indexEntry?.mediaType ?: MediaType.OTHER
    val isVideo = mediaType.isPlayable
    val title = entry?.title ?: indexEntry?.title.orEmpty()
    val credit = entry?.displayCredit ?: indexEntry?.displayCredit
    val teaser = entry?.explanation?.takeIf { it.isNotBlank() }
    val hydrationFailed = page.hydration == Hydration.FAILED
    val favoriteActionLabel = stringResource(
        if (isFavourite) R.string.feed_remove_favorite else R.string.feed_save,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        FeedImageLayer(
            page = page,
            scaling = scaling,
            isVideo = isVideo,
            contentDescription = entry?.altText ?: title.ifBlank { null },
        )

        // A single tap hides the reading overlay and the app's bottom bar; a double tap
        // favourites this APOD. The layer sits below the chrome, so buttons and links keep
        // their own clicks.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(
                        if (chromeVisible) R.string.feed_hide_chrome else R.string.feed_show_chrome,
                    ),
                    onClick = onToggleChrome,
                    onDoubleClick = { actions.onToggleFavourite(date) },
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(favoriteActionLabel) {
                            actions.onToggleFavourite(date)
                            true
                        },
                    )
                },
        )

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // The top scrim mirrors the reading gradient at the bottom, so the glass buttons
                // and the wordmark keep their contrast over a bright photograph.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactHeight) 100.dp else 200.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to MohakashColors.Background.copy(alpha = 0.94f),
                                0.46f to MohakashColors.Background.copy(alpha = 0.52f),
                                1f to Color.Transparent,
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = MohakashDimens.SidePadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isVideo) {
                        VideoBadge()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FeedGlassIconButton(
                            painter = painterResource(R.drawable.ic_search_24),
                            contentDescription = stringResource(R.string.feed_search),
                            onClick = actions.onOpenSearch,
                        )
                        Spacer(Modifier.weight(1f))
                        FeedBrandMark()
                        Spacer(Modifier.weight(1f))
                        FeedGlassIconButton(
                            painter = painterResource(R.drawable.ic_settings_24),
                            contentDescription = stringResource(R.string.feed_settings),
                            onClick = actions.onOpenSettings,
                        )
                    }
                }
            }
        }

        // ---- Bottom gradient, reading copy and the action rail -------------------------
        // The gradient is functional rather than decorative: the picture stays visible at the
        // top while the title, teaser and controls remain readable over its bottom edge.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.66f to MohakashColors.Background.copy(alpha = 0.62f),
                                0.84f to MohakashColors.Background.copy(alpha = 0.9f),
                                1f to MohakashColors.Background,
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = MohakashDimens.SidePadding,
                            end = 92.dp,
                            bottom = (if (compactHeight) 8.dp else 14.dp) + bottomBarInset,
                        ),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val dateLabel = feedDisplayDate(date).uppercase()
                    if (dateLabel.isNotBlank() && !compactHeight) {
                        FeedDateRail(dateLabel)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!credit.isNullOrBlank()) {
                            Text(
                                text = credit,
                                style = MohakashTextStyles.Caption,
                                color = MohakashColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = title.ifBlank { stringResource(R.string.detail_image_unavailable) },
                            style = MohakashTextStyles.PageTitle,
                            color = MohakashColors.Text,
                            maxLines = if (compactHeight) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (teaser != null && !compactHeight) {
                            Text(
                                text = teaser,
                                style = MohakashTextStyles.BodyBase,
                                color = MohakashColors.TextPreview,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (isOffline) {
                            Text(
                                text = stringResource(R.string.feed_explanation_unavailable),
                                style = MohakashTextStyles.Caption,
                                color = MohakashColors.TextMuted,
                            )
                        }
                        if (hydrationFailed) {
                            HydrationFailureRow(onRetry = { actions.onRetryHydration(date) })
                        }
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when {
                                isVideo -> WatchOnApodPill(
                                    onClick = {
                                        val url = entry?.apodUrl ?: indexEntry?.apodUrl
                                        if (!url.isNullOrBlank()) actions.onOpenLink(url) else actions.onOpenEntry(date)
                                    },
                                )

                                isOffline -> TextAction(
                                    text = stringResource(R.string.feed_retry),
                                    onClick = actions.onRefresh,
                                )

                                else -> WallpaperPill(
                                    active = wallpaperActive,
                                    enabled = entry != null,
                                    onClick = { actions.onOpenWallpaper(date) },
                                )
                            }
                            TextAction(
                                text = stringResource(R.string.feed_full_entry),
                                onClick = { actions.onOpenEntry(date) },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 10.dp,
                            bottom = (if (compactHeight) 8.dp else 18.dp) + bottomBarInset,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    FeedRailAction(
                        painter = painterResource(
                            if (isFavourite) R.drawable.ic_heart_filled_24 else R.drawable.ic_heart_24,
                        ),
                        label = stringResource(if (isFavourite) R.string.feed_saved_state else R.string.feed_save_label),
                        contentDescription = stringResource(
                            if (isFavourite) R.string.feed_remove_favorite else R.string.feed_save,
                        ),
                        tint = if (isFavourite) MohakashColors.Accent else MohakashColors.Text,
                        enabled = entry != null,
                        onClick = { actions.onToggleFavourite(date) },
                    )
                    if (!isVideo && !isOffline) {
                        FeedRailAction(
                            painter = painterResource(R.drawable.ic_download_24),
                            label = stringResource(R.string.feed_download_label),
                            contentDescription = stringResource(R.string.feed_download),
                            tint = MohakashColors.Text,
                            enabled = entry != null,
                            onClick = { actions.onSaveImage(date) },
                        )
                    }
                    FeedRailAction(
                        painter = painterResource(R.drawable.ic_share_24),
                        label = stringResource(R.string.feed_share),
                        contentDescription = stringResource(R.string.feed_share),
                        tint = MohakashColors.Text,
                        onClick = { actions.onShare(date) },
                    )
                }
            }
        }
    }
}

/** The vertical date on the left edge, in the style of a short-video caption rail. */
@Composable
private fun FeedDateRail(label: String) {
    Column(
        modifier = Modifier
            .offset(y = (-24).dp)
            .width(22.dp)
            .height(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(24.dp)
                .background(MohakashColors.Accent),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MohakashTextStyles.Caption.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                ),
                color = MohakashColors.TextMuted,
                maxLines = 1,
                softWrap = false,
                // The text is measured horizontally, then rotated into the narrow rail.
                modifier = Modifier
                    .requiredWidth(140.dp)
                    .rotate(-90f),
            )
        }
    }
}

/** One labelled action in the right-hand rail. */
@Composable
private fun FeedRailAction(
    painter: Painter,
    label: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MohakashColors.Surface.copy(alpha = 0.62f))
                .border(1.dp, MohakashColors.BorderStrong, CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = if (enabled) tint else tint.copy(alpha = 0.45f),
                modifier = Modifier.size(23.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MohakashTextStyles.Caption.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MohakashColors.Text,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The small top-centre brand mark; it balances the reading controls without competing with them. */
@Composable
private fun FeedBrandMark() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.feed_brand),
            style = MohakashTextStyles.Caption.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.2.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MohakashColors.Text.copy(alpha = 0.92f),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(1.dp)
                .background(MohakashColors.Accent.copy(alpha = 0.8f)),
        )
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

/** A 44 dp glass circle — the icon buttons of the overlay chrome. */
@Composable
private fun FeedGlassIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
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
    }
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

/**
 * A calm skeleton that matches the loaded page layout: the image block and the overlay block keep
 * their final size, so nothing jumps when the first page arrives.
 */
@Composable
internal fun FeedSkeleton(modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.feed_loading)
    Box(
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
    bottomBarInset: Dp = 0.dp,
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
            .padding(horizontal = 40.dp)
            .padding(bottom = bottomBarInset),
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
