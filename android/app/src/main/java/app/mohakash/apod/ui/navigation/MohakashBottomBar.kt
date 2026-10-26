package app.mohakash.apod.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** The four regular destinations; the random button sits in the middle. */
enum class BottomDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
    val activeIconRes: Int,
) {
    FEED(Routes.TODAY, R.string.nav_feed, R.drawable.ic_today_24, R.drawable.ic_today_24),
    ARCHIVE(Routes.ARCHIVE, R.string.nav_archive, R.drawable.ic_archive_24, R.drawable.ic_archive_24),
    SAVED(Routes.SAVED, R.string.nav_saved, R.drawable.ic_heart_24, R.drawable.ic_heart_filled_24),
    SETTINGS(Routes.SETTINGS_TAB, R.string.settings_title, R.drawable.ic_settings_24, R.drawable.ic_settings_24),
}

private val LeftDestinations = listOf(BottomDestination.FEED, BottomDestination.ARCHIVE)
private val RightDestinations = listOf(BottomDestination.SAVED, BottomDestination.SETTINGS)

/**
 * The bottom navigation bar.
 *
 * It stays solid `SurfaceRaised` — not translucent — so the selected tab and its label keep their
 * contrast over any picture.
 */
@Composable
fun MohakashBottomBar(
    currentRoute: String?,
    onSelect: (BottomDestination) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MohakashColors.SurfaceRaised),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MohakashColors.Border),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = MohakashDimens.BottomNavHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeftDestinations.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
            RandomBarItem(
                onClick = onRandom,
                modifier = Modifier.weight(1f),
            )
            RightDestinations.forEach { destination ->
                BottomBarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One item of the bottom navigation bar. */
@Composable
private fun BottomBarItem(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon: Painter = painterResource(if (selected) destination.activeIconRes else destination.iconRes)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (selected) MohakashColors.Text else MohakashColors.TextMuted,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MohakashTextStyles.Caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MohakashColors.Text else MohakashColors.TextMuted,
        )
    }
}

/** The highlighted dice in the middle of the navigation bar. */
@Composable
private fun RandomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MohakashColors.Accent)
                .border(2.dp, MohakashColors.Background, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dice_24),
                contentDescription = stringResource(R.string.nav_random_description),
                tint = MohakashColors.OnAccent,
                modifier = Modifier.size(27.dp),
            )
        }
        Text(
            text = stringResource(R.string.nav_random),
            style = MohakashTextStyles.Caption,
            color = MohakashColors.TextMuted,
        )
    }
}
