package app.mohakash.apod.ui.wallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.WallpaperScaling
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.io.File

/**
 * The live wallpaper sheet.
 *
 * It shows the picture that is currently used, lets the user pick the scaling mode, switch the
 * daily update on or off and opens the system preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(
    scaling: WallpaperScaling,
    updateDaily: Boolean,
    previewPath: String?,
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
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MohakashColors.BorderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MohakashDimens.SidePadding, end = MohakashDimens.SidePadding)
                .padding(bottom = 28.dp),
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(MohakashColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewPath != null) {
                        AsyncImage(
                            model = File(previewPath),
                            contentDescription = stringResource(R.string.wallpaper_preview),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.wallpaper_preview),
                            style = MohakashTextStyles.Caption,
                            color = MohakashColors.TextMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.wallpaper_current_image),
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.wallpaper_update_daily),
                            style = MohakashTextStyles.UiSmall,
                            color = MohakashColors.Text,
                        )
                        Switch(
                            checked = updateDaily,
                            onCheckedChange = onUpdateDailyChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MohakashColors.OnAccent,
                                checkedTrackColor = MohakashColors.Accent,
                                checkedBorderColor = MohakashColors.Accent,
                                uncheckedThumbColor = MohakashColors.TextMuted,
                                uncheckedTrackColor = MohakashColors.Surface,
                                uncheckedBorderColor = MohakashColors.BorderStrong,
                            ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.wallpaper_preview_and_set),
                    onClick = onPreviewAndSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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
}
