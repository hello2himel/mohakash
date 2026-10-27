package app.mohakash.apod.ui.archive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.IconActionButton
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.history.HistoryScreen
import app.mohakash.apod.ui.saved.SavedScreen
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The personal library, reached from the bottom-nav Archive slot.
 *
 * Three tabs share the slot: discovery History, the complete Archive and saved Favorites.
 * History deliberately records only random discoveries and carousel wallpaper choices; normal
 * APOD browsing is not logged.
 */
@Composable
fun ArchiveHubScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseFeed: () -> Unit,
    openOnSaved: Boolean = false,
    onOpenOnSavedConsumed: () -> Unit = {},
    bottomBarInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // Archive is still the default when the destination is opened from the bottom bar.
    var selectedTab by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(openOnSaved) {
        if (openOnSaved) {
            selectedTab = 2
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

        LibraryTabs(
            labels = listOf(
                stringResource(R.string.history_title),
                stringResource(R.string.archive_title),
                stringResource(R.string.favorites_title),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 4.dp, bottom = 8.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> HistoryScreen(
                    onOpenEntry = onOpenEntry,
                    onBrowseFeed = onBrowseFeed,
                    bottomContentPadding = bottomBarInset,
                )

                1 -> ArchiveScreen(
                    onOpenEntry = onOpenEntry,
                    onOpenSettings = onOpenSettings,
                    showTopBar = false,
                    bottomContentPadding = bottomBarInset,
                )

                else -> SavedScreen(
                    onOpenEntry = onOpenEntry,
                    onBrowseFeed = onBrowseFeed,
                    onOpenSettings = onOpenSettings,
                    showTopBar = false,
                    bottomContentPadding = bottomBarInset,
                )
            }
        }
    }
}


/** Equal-width tab pills so three localized labels fit narrow screens without clipping. */
@Composable
private fun LibraryTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(if (selected) MohakashColors.Accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = if (selected) MohakashTextStyles.UiSmallSemiBold else MohakashTextStyles.UiSmall,
                    color = if (selected) MohakashColors.OnAccent else MohakashColors.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
