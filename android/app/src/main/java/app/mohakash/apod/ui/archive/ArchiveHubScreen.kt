package app.mohakash.apod.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.saved.SavedScreen
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens

/**
 * The combined Archive + Saved destination.
 *
 * The two lists are different views of the same personal library, so they share one bottom-nav
 * slot and a quiet segmented switch instead of competing for separate tabs.
 */
@Composable
fun ArchiveHubScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseFeed: () -> Unit,
    openOnSaved: Boolean = false,
    onOpenOnSavedConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(openOnSaved) {
        if (openOnSaved) {
            selectedTab = 1
            onOpenOnSavedConsumed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.archive_title),
            actions = {
                IconActionButton(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = stringResource(R.string.feed_settings),
                    onClick = onOpenSettings,
                    tint = MohakashColors.TextMuted,
                )
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.archive_title),
                    stringResource(R.string.saved_title),
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                ArchiveScreen(
                    onOpenEntry = onOpenEntry,
                    onOpenSettings = onOpenSettings,
                    showTopBar = false,
                )
            } else {
                SavedScreen(
                    onOpenEntry = onOpenEntry,
                    onBrowseFeed = onBrowseFeed,
                    onOpenSettings = onOpenSettings,
                    showTopBar = false,
                )
            }
        }
    }
}
