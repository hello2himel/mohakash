package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The pull-to-refresh affordance at the head of the feed.
 *
 * The gesture itself is handled by Material 3's `PullToRefreshBox`, which is only active when the
 * nested scroll is at the beginning — i.e. exactly on the latest page. This is the matching
 * indicator: a small solid pill that follows the pull, in the same restrained language as the
 * rest of the overlay chrome (no gradient, no spinner while idle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.FeedPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
) {
    val fraction = state.distanceFraction
    val visible = isRefreshing || fraction > 0.01f
    if (!visible) return
    val alpha = if (isRefreshing) 1f else fraction.coerceIn(0f, 1f)
    val label = when {
        isRefreshing -> stringResource(R.string.feed_refreshing)
        fraction >= 1f -> stringResource(R.string.feed_release_refresh)
        else -> stringResource(R.string.feed_pull_refresh)
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(MohakashColors.Surface.copy(alpha = 0.88f))
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                color = MohakashColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = MohakashTextStyles.Caption,
            color = MohakashColors.Text,
        )
    }
}
