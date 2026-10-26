package app.mohakash.apod.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.ui.components.NoteBox
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.theme.MohakashDimens

/**
 * The actions under the title.
 *
 * Image entries can be saved to the gallery and used as the live wallpaper — both flows are the
 * existing ones (legacy permission request, wallpaper sheet with the letterbox/fill choice).
 * Video and interactive entries keep the existing behaviour: they are watched on the original
 * APOD page and cannot be used as a wallpaper.
 */
@Composable
internal fun EntryActionBar(
    entry: ApodEntry,
    onSaveImage: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apodUrl = entry.apodUrl?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 18.dp),
    ) {
        if (entry.mediaType.isImage) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.detail_save_image),
                    onClick = onSaveImage,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.detail_set_wallpaper),
                    onClick = onSetWallpaper,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.detail_watch_original),
                onClick = { apodUrl?.let(onOpenLink) },
                enabled = apodUrl != null,
                modifier = Modifier.fillMaxWidth(),
            )
            NoteBox(
                text = stringResource(R.string.detail_video_note),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
