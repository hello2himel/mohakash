package app.mohakash.apod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.BuildConfig
import app.mohakash.apod.R
import app.mohakash.apod.core.ByteFormatting
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.InfoRow
import app.mohakash.apod.ui.components.LinkRow
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.SectionLabel
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.components.SwitchRow
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The settings screen.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        SettingsViewModel(
            settingsStore = container.settingsStore,
            cacheManager = container.cacheManager,
            syncController = container.syncController,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val cacheClearedMessage = stringResource(R.string.message_cache_cleared)
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                SettingsEvent.CacheCleared -> snackbarHostState.showSnackbar(cacheClearedMessage)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_section_wallpaper))

            SwitchRow(
                title = stringResource(R.string.settings_update_automatically),
                subtitle = stringResource(R.string.settings_update_automatically_hint),
                checked = state.updateAutomatically,
                onCheckedChange = viewModel::setUpdateAutomatically,
                showDivider = false,
            )
            SwitchRow(
                title = stringResource(R.string.settings_notify_on_update),
                subtitle = stringResource(R.string.settings_notify_on_update_hint),
                checked = state.notifyOnUpdate,
                onCheckedChange = viewModel::setNotifyOnUpdate,
                showDivider = false,
            )
            SwitchRow(
                title = stringResource(R.string.settings_prefer_hd),
                subtitle = stringResource(R.string.settings_prefer_hd_hint),
                checked = state.preferHd,
                onCheckedChange = viewModel::setPreferHd,
                showDivider = false,
            )
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                checked = state.wifiOnly,
                onCheckedChange = viewModel::setWifiOnly,
                showDivider = false,
            )

            ScalingRow(
                scaling = state.scaling,
                onScalingChange = viewModel::setScaling,
            )

            SectionLabel(
                text = stringResource(R.string.settings_section_storage),
                modifier = Modifier.padding(top = 24.dp),
            )

            CacheRow(
                sizeBytes = state.cacheSizeBytes,
                onClear = { showClearCacheDialog = true },
            )

            SectionLabel(
                text = stringResource(R.string.settings_section_about),
                modifier = Modifier.padding(top = 24.dp),
            )

            InfoRow(
                title = stringResource(R.string.settings_last_synced),
                value = TimeFormatting.formatSyncedAgo(state.lastSyncAt, System.currentTimeMillis()),
                showDivider = false,
            )
            LinkRow(
                text = stringResource(R.string.settings_content_rights),
                onClick = { onOpenLink(BuildConfig.CONTENT_RIGHTS_URL) },
            )
            LinkRow(
                text = stringResource(R.string.settings_source_code),
                onClick = { onOpenLink(BuildConfig.REPOSITORY_URL) },
                showDivider = false,
            )
            InfoRow(
                title = stringResource(R.string.settings_version),
                value = BuildConfig.VERSION_NAME,
                showDivider = false,
            )

            Text(
                text = stringResource(R.string.settings_rights_note),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }

    if (showClearCacheDialog) {
        ClearCacheDialog(
            sizeBytes = state.cacheSizeBytes,
            onDismiss = { showClearCacheDialog = false },
            onConfirm = {
                showClearCacheDialog = false
                viewModel.clearCache()
            },
        )
    }
}

/** The *Image scaling* row of the settings screen. */
@Composable
private fun ScalingRow(
    scaling: WallpaperScaling,
    onScalingChange: (WallpaperScaling) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_image_scaling),
            style = MohakashTextStyles.UiMedium,
            color = MohakashColors.Text,
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.wallpaper_scaling_fill),
                stringResource(R.string.wallpaper_scaling_fit),
            ),
            selectedIndex = if (scaling == WallpaperScaling.FILL) 0 else 1,
            onSelect = { index ->
                onScalingChange(if (index == 0) WallpaperScaling.FILL else WallpaperScaling.FIT)
            },
        )
    }
}

/** The *Image cache* row with the *Clear* button. */
@Composable
private fun CacheRow(sizeBytes: Long, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.settings_image_cache),
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
            )
            Text(
                text = stringResource(R.string.settings_image_cache_size, ByteFormatting.format(sizeBytes)),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
        }
        val shape = RoundedCornerShape(MohakashDimens.PillRadius)
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(shape)
                .border(1.dp, MohakashColors.BorderStrong, shape)
                .clickable(onClick = onClear)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.settings_clear),
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.Text,
            )
        }
    }
}

/** The dialog that asks before the image cache is removed. */
@Composable
private fun ClearCacheDialog(
    sizeBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(MohakashDimens.DialogRadius))
                .background(MohakashColors.Surface)
                .border(
                    1.dp,
                    MohakashColors.Border,
                    RoundedCornerShape(MohakashDimens.DialogRadius),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.clear_cache_title),
                    style = MohakashTextStyles.SheetTitle,
                    color = MohakashColors.Text,
                )
                Text(
                    text = stringResource(
                        R.string.clear_cache_body,
                        ByteFormatting.format(sizeBytes),
                    ),
                    style = MohakashTextStyles.UiSmall,
                    color = MohakashColors.TextBody,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DialogTextButton(
                        text = stringResource(R.string.clear_cache_cancel),
                        onClick = onDismiss,
                        color = MohakashColors.Accent,
                    )
                    DialogTextButton(
                        text = stringResource(R.string.clear_cache_confirm),
                        onClick = onConfirm,
                        color = MohakashColors.OnAccent,
                        filled = true,
                    )
                }
            }
        }
    }
}

/** A text button inside the clear-cache dialog. */
@Composable
private fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(MohakashColors.Accent) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (filled) 20.dp else 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.UiSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
