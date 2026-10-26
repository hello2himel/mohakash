package app.mohakash.apod.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.BrandMarkBadge
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** Feed on the left, Archive on the right; the random action is the raised key. */
enum class BottomDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
    val activeIconRes: Int,
) {
    FEED(Routes.TODAY, R.string.nav_feed, R.drawable.ic_today_24, R.drawable.ic_today_24),
    ARCHIVE(Routes.ARCHIVE, R.string.nav_archive, R.drawable.ic_archive_24, R.drawable.ic_archive_24),
}

/**
 * The Launch Cradle.
 *
 * One smooth floating plate carries both destinations. The selected destination is marked by a
 * short underline; the oversized rocket sits on top of the plate, centred on both axes.
 */
@Composable
fun MohakashBottomBar(
    currentRoute: String?,
    onSelect: (BottomDestination) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val archiveSelected = currentRoute == BottomDestination.ARCHIVE.route
    val plateShape = RoundedCornerShape(MohakashDimens.CradleRadius)

    // The plate grows when the platform font scale would otherwise clip its labels.
    val fontScale = LocalDensity.current.fontScale
    val plateHeight = when {
        fontScale >= 2f -> MohakashDimens.CradleHeightLarge
        fontScale >= 1.3f -> MohakashDimens.CradleHeightComfort
        else -> MohakashDimens.CradleHeight
    }
    val cellWidth = if (fontScale >= 1.6f) {
        MohakashDimens.CradleCellWidthLarge
    } else {
        MohakashDimens.CradleCellWidth
    }
    val plateWidth = 20.dp + cellWidth * 2 + MohakashDimens.CradleKeyTarget
    val cellHeight = plateHeight
    val barHeight = plateHeight + MohakashDimens.CradleBarExtra

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = -MohakashDimens.CradleBottomInset)
                        .width(plateWidth)
                        .height(plateHeight)
                        .shadow(10.dp, plateShape, clip = false)
                        .background(MohakashColors.Surface, plateShape)
                        .border(1.dp, MohakashColors.BorderStrong, plateShape),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CradleDestination(
                            destination = BottomDestination.FEED,
                            selected = currentRoute == BottomDestination.FEED.route,
                            onClick = { onSelect(BottomDestination.FEED) },
                            width = cellWidth,
                            height = cellHeight,
                        )
                        Spacer(Modifier.weight(1f))
                        CradleDestination(
                            destination = BottomDestination.ARCHIVE,
                            selected = archiveSelected,
                            onClick = { onSelect(BottomDestination.ARCHIVE) },
                            width = cellWidth,
                            height = cellHeight,
                        )
                    }
                }

                CradleKey(
                    onClick = onRandom,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 1.dp),
                )
            }
        }
    }
}

/** One labelled destination inside the plate. */
@Composable
private fun CradleDestination(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    width: Dp,
    height: Dp,
) {
    val icon: Painter = painterResource(
        if (selected) destination.activeIconRes else destination.iconRes,
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (selected) MohakashColors.Text else MohakashColors.TextMuted,
                modifier = Modifier.size(MohakashDimens.CradleIcon),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(destination.labelRes),
                style = MohakashTextStyles.Caption.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) MohakashColors.Text else MohakashColors.TextMuted,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(20.dp)
                .height(2.dp)
                .background(if (selected) MohakashColors.Accent else Color.Transparent),
        )
    }
}

/** The random rocket, mounted in the plate's top notch. */
@Composable
private fun CradleKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.nav_random_description)
    Box(
        modifier = modifier
            .size(MohakashDimens.CradleKeyTarget)
            .semantics { contentDescription = description }
            .clickable(
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BrandMarkBadge(
            badgeSize = MohakashDimens.CradleKeySize,
            iconSize = MohakashDimens.CradleKeyIcon,
        )
    }
}
