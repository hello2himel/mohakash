package app.mohakash.apod.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** The three primary destinations of the app. */
enum class BottomDestination(val route: String, val labelRes: Int, val iconRes: Int) {
    TODAY(Routes.TODAY, R.string.nav_today, R.drawable.ic_today_24),
    ARCHIVE(Routes.ARCHIVE, R.string.nav_archive, R.drawable.ic_archive_24),
    SAVED(Routes.SAVED, R.string.nav_saved, R.drawable.ic_heart_24),
}

/**
 * The bottom navigation bar.
 */
@Composable
fun MohakashBottomBar(
    currentRoute: String?,
    onSelect: (BottomDestination) -> Unit,
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
                .height(MohakashDimens.BottomNavHeight)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestination.entries.forEach { destination ->
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
    val icon: Painter = painterResource(destination.iconRes)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clickable(onClick = onClick)
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
