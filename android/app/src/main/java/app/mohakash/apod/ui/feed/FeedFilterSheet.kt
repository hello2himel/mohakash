package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.FilterChipItem
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The filter sheet of the feed: all years, all media and jump-to-date.
 *
 * There is no second "older years" dialog any more — the years of the archive index are simply
 * listed, which is what the combined feed makes possible. The sheet is stateless towards the
 * ViewModel: selections are local until *Show N pictures* is pressed, so dismissing keeps the
 * previous filters (a deliberate, documented behaviour).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedFilterSheet(
    filters: FeedFilters,
    availableYears: List<Int>,
    allItems: List<FeedDateItem>,
    archiveFirstDate: String?,
    archiveLastDate: String?,
    onDismiss: () -> Unit,
    onApply: (FeedFilters, String?) -> Unit,
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
        FeedFilterSheetContent(
            filters = filters,
            availableYears = availableYears,
            allItems = allItems,
            archiveFirstDate = archiveFirstDate,
            archiveLastDate = archiveLastDate,
            onApply = onApply,
        )
    }
}

/** The stateless body of the filter sheet — used by the preview as well. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FeedFilterSheetContent(
    filters: FeedFilters,
    availableYears: List<Int>,
    allItems: List<FeedDateItem>,
    archiveFirstDate: String?,
    archiveLastDate: String?,
    onApply: (FeedFilters, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedYear by remember(filters) { mutableStateOf(filters.year) }
    var selectedMedia by remember(filters) { mutableStateOf(filters.media) }
    var jumpToDate by remember(filters) { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val previewFilters = FeedFilters(year = selectedYear, media = selectedMedia)
    val resultCount = remember(allItems, previewFilters) {
        FeedPaging.filtered(allItems, previewFilters).size
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MohakashDimens.SidePadding)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.filter_title),
            style = MohakashTextStyles.SheetTitle,
            color = MohakashColors.Text,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.filter_year),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
                verticalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
            ) {
                FilterChipItem(
                    text = stringResource(R.string.filter_all_years),
                    selected = selectedYear == null,
                    onClick = { selectedYear = null },
                )
                availableYears.forEach { year ->
                    FilterChipItem(
                        text = year.toString(),
                        selected = year == selectedYear,
                        onClick = { selectedYear = year },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.filter_type),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.filter_all),
                    stringResource(R.string.filter_images),
                    stringResource(R.string.filter_videos),
                ),
                selectedIndex = when (selectedMedia) {
                    FeedMediaFilter.ALL -> 0
                    FeedMediaFilter.IMAGES -> 1
                    FeedMediaFilter.VIDEOS -> 2
                },
                onSelect = { index ->
                    selectedMedia = when (index) {
                        1 -> FeedMediaFilter.IMAGES
                        2 -> FeedMediaFilter.VIDEOS
                        else -> FeedMediaFilter.ALL
                    }
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.filter_jump_to_date),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(MohakashDimens.CardRadius))
                    .background(MohakashColors.Background)
                    .border(
                        1.dp,
                        MohakashColors.BorderStrong,
                        RoundedCornerShape(MohakashDimens.CardRadius),
                    )
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = jumpToDate ?: stringResource(R.string.filter_jump_to_date_placeholder),
                    style = MohakashTextStyles.UiSmall,
                    color = if (jumpToDate == null) MohakashColors.TextMuted else MohakashColors.Text,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.filter_reset),
                onClick = {
                    selectedYear = null
                    selectedMedia = FeedMediaFilter.ALL
                    jumpToDate = null
                },
                modifier = Modifier.width(110.dp),
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium,
            )
            PrimaryButton(
                text = pluralStringResource(
                    R.plurals.filter_show_entries,
                    resultCount,
                    resultCount,
                ),
                onClick = {
                    onApply(
                        FeedFilters(year = selectedYear, media = selectedMedia),
                        jumpToDate,
                    )
                },
                modifier = Modifier.weight(1f),
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }

    if (showDatePicker) {
        val firstMillis = remember(archiveFirstDate) { archiveFirstDate?.let(::isoToUtcMillis) }
        val lastMillis = remember(archiveLastDate) { archiveLastDate?.let(::isoToUtcMillis) }
        val firstYear = archiveFirstDate?.take(4)?.toIntOrNull() ?: 1995
        val lastYear = archiveLastDate?.take(4)?.toIntOrNull() ?: 2100
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = null,
            yearRange = firstYear..lastYear,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    if (firstMillis != null && utcTimeMillis < firstMillis) return false
                    if (lastMillis != null && utcTimeMillis > lastMillis) return false
                    return true
                }

                override fun isSelectableYear(year: Int): Boolean = year in firstYear..lastYear
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> jumpToDate = utcMillisToIso(millis) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.filter_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.clear_cache_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun isoToUtcMillis(date: String): Long? {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    format.isLenient = false
    return runCatching { format.parse(date)?.time }.getOrNull()
}

private fun utcMillisToIso(millis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(millis)
}
