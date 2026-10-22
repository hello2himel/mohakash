package app.mohakash.apod.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.components.switchColors
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The live wallpaper sheet.
 *
 * It previews the picture that will be installed, lets the user pick the scaling mode, switch
 * the daily update on or off and opens the system preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(
    scaling: WallpaperScaling,
    updateDaily: Boolean,
    preview: WallpaperPreview,
    caption: String,
    onDismiss: () -> Unit,
    onScalingChange: (WallpaperScaling) -> Unit,
    onUpdateDailyChange: (Boolean) -> Unit,
    onPreviewAndSet: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MohakashColors.Surface,
        contentColor = MohakashColors.Text,
        shape = RoundedCornerShape(
            topStart = MohakashDimens.SheetRadius,
            topEnd = MohakashDimens.SheetRadius,
        ),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MohakashColors.BorderStrong),
            )
        },
    ) {
        WallpaperSheetContent(
            scaling = scaling,
            updateDaily = updateDaily,
            preview = preview,
            caption = caption,
            onScalingChange = onScalingChange,
            onUpdateDailyChange = onUpdateDailyChange,
            onPreviewAndSet = onPreviewAndSet,
        )
    }
}

/**
 * The stateless body of the wallpaper sheet — usable in a preview without the modal container.
 */
@Composable
internal fun WallpaperSheetContent(
    scaling: WallpaperScaling,
    updateDaily: Boolean,
    preview: WallpaperPreview,
    caption: String,
    onScalingChange: (WallpaperScaling) -> Unit,
    onUpdateDailyChange: (Boolean) -> Unit,
    onPreviewAndSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // The sheet scrolls when a very large font scale makes it taller than the screen.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.wallpaper_sheet_title),
            style = MohakashTextStyles.SheetTitle,
            color = MohakashColors.Text,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(MohakashDimens.WallpaperPreviewWidth)
                    .aspectRatio(9f / 19f)
                    .clip(RoundedCornerShape(MohakashDimens.WallpaperPreviewRadius))
                    .background(MohakashColors.Background)
                    .border(
                        1.dp,
                        MohakashColors.BorderStrong,
                        RoundedCornerShape(MohakashDimens.WallpaperPreviewRadius),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (preview.model != null) {
                    AsyncImage(
                        model = preview.model,
                        contentDescription = stringResource(R.string.wallpaper_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wallpaper_preview),
                        style = MohakashTextStyles.PreviewLabel,
                        color = MohakashColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 200.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.wallpaper_selected_image),
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
                    )
                    Text(
                        text = caption,
                        style = MohakashTextStyles.UiMedium,
                        color = MohakashColors.Text,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.wallpaper_scaling),
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .toggleable(
                            value = updateDaily,
                            role = Role.Switch,
                            onValueChange = onUpdateDailyChange,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.wallpaper_update_daily),
                        style = MohakashTextStyles.UiSmall,
                        color = MohakashColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = updateDaily,
                        onCheckedChange = null,
                        colors = MohakashColors.switchColors(),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(R.string.wallpaper_preview_and_set),
                onClick = onPreviewAndSet,
                modifier = Modifier.fillMaxWidth(),
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = stringResource(R.string.wallpaper_helper),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
