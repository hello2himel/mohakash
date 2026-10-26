package app.mohakash.apod.ui.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.domain.model.ApodEntry
import app.mohakash.apod.domain.model.MediaType
import app.mohakash.apod.ui.components.InfoRow
import app.mohakash.apod.ui.components.LinkRow
import app.mohakash.apod.ui.components.NoteBox
import app.mohakash.apod.ui.components.SectionLabel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.util.Locale

/**
 * The text under the picture: date, media chip, credit and title.
 *
 * The date is human readable (`26 October 2026`); the ISO date stays available in the metadata
 * section for users who want the exact value. The credit is never invented — the line is omitted
 * when the API has none, and the metadata row says *Not provided*.
 */
@Composable
internal fun EntryHeading(
    entry: ApodEntry,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val displayDate = EntryDetailFormatting.formatDisplayDate(entry.date, locale)
    val credit = entry.displayCredit?.trim()?.takeIf { it.isNotEmpty() }
    val creditLine = credit?.let { stringResource(R.string.entry_credit_value, it) }
    val metaLine = listOfNotNull(displayDate.takeIf { it.isNotEmpty() }, creditLine)
        .joinToString(" · ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
        }
        Text(
            text = entry.title,
            style = MohakashTextStyles.PageTitle,
            color = MohakashColors.Text,
        )
    }
}

/** The small outlined media label (`Image`, `Video`, `Interactive`). */
@Composable
internal fun EntryMediaChip(mediaType: MediaType, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .heightIn(min = 24.dp)
            .clip(shape)
            .border(1.dp, MohakashColors.BorderStrong, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entryMediaLabelText(mediaType),
            style = MohakashTextStyles.Caption,
            color = MohakashColors.TextMuted,
        )
    }
}

/** The localized label of a media type. */
@Composable
internal fun entryMediaLabelText(mediaType: MediaType): String = stringResource(
    when (EntryDetailFormatting.mediaLabel(mediaType)) {
        EntryMediaLabel.IMAGE -> R.string.entry_media_image
        EntryMediaLabel.VIDEO -> R.string.entry_media_video
        EntryMediaLabel.INTERACTIVE -> R.string.entry_media_interactive
    },
)

/** The complete explanation of the entry, one paragraph per text block. */
@Composable
internal fun EntryExplanation(
    entry: ApodEntry,
    modifier: Modifier = Modifier,
) {
    val paragraphs = EntryDetailFormatting.explanationParagraphs(entry)
    if (paragraphs.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                style = MohakashTextStyles.BodyMedium,
                color = MohakashColors.TextBody,
            )
        }
    }
}

/**
 * The *all metadata* section: every useful field of the record, plus the external links.
 *
 * Empty values are omitted; the credit is the only row that is always shown because *who made
 * the picture* is part of the entry even when the publisher does not say.
 */
@Composable
internal fun EntryMetaSection(
    entry: ApodEntry,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val rows = EntryDetailFormatting.metadataRows(entry, locale)
    val apodUrl = entry.apodUrl?.trim()?.takeIf { it.isNotEmpty() }
    val hdImageUrl = entry.hdImageUrl?.trim()?.takeIf { it.isNotEmpty() }
    val showLinks = !entry.mediaType.isPlayable && (apodUrl != null || hdImageUrl != null)
    val imageNote = EntryDetailFormatting.imageStatusNote(entry.imageStatus)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 28.dp),
    ) {
        SectionLabel(stringResource(R.string.entry_meta_section))
        rows.forEachIndexed { index, row ->
            InfoRow(
                title = entryMetadataLabel(row.key),
                value = entryMetadataValue(row.value),
                showDivider = index > 0,
            )
        }

        if (imageNote != null) {
            NoteBox(
                text = stringResource(
                    when (imageNote) {
                        EntryImageNote.PLACEHOLDER -> R.string.entry_image_placeholder_note
                        EntryImageNote.MISSING -> R.string.entry_image_missing_note
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        }

        if (showLinks) {
            SectionLabel(
                text = stringResource(R.string.entry_links_section),
                modifier = Modifier.padding(top = 24.dp),
            )
            apodUrl?.let { url ->
                LinkRow(
                    text = stringResource(R.string.detail_original_page),
                    onClick = { onOpenLink(url) },
                    showDivider = false,
                )
            }
            hdImageUrl?.let { url ->
                LinkRow(
                    text = stringResource(R.string.detail_hd_image),
                    onClick = { onOpenLink(url) },
                    showDivider = apodUrl != null,
                )
            }
        }
    }
}

/** The localized label of a metadata row. */
@Composable
private fun entryMetadataLabel(key: EntryMetadataKey): String = stringResource(
    when (key) {
        EntryMetadataKey.DATE -> R.string.entry_meta_date
        EntryMetadataKey.MEDIA_TYPE -> R.string.entry_meta_media_type
        EntryMetadataKey.CREDIT -> R.string.entry_meta_credit
        EntryMetadataKey.UPDATED -> R.string.entry_meta_updated
        EntryMetadataKey.SOURCE -> R.string.entry_meta_source
    },
)

/** The displayable value of a metadata row. */
@Composable
private fun entryMetadataValue(value: EntryMetadataValue): String = when (value) {
    is EntryMetadataValue.Text -> value.text
    is EntryMetadataValue.Media -> entryMediaLabelText(value.mediaType)
    EntryMetadataValue.NotProvided -> stringResource(R.string.entry_not_provided)
}
