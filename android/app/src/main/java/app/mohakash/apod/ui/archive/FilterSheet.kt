package app.mohakash.apod.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
 * The filter sheet of the archive.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    state: ArchiveUiState,
    onDismiss: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onTypeSelected: (MediaFilter) -> Unit,
    onJumpToDate: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedYear by remember { mutableStateOf(state.selectedYear) }
    var selectedType by remember { mutableStateOf(state.typeFilter) }
    var jumpToDate by remember { mutableStateOf(state.jumpToDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val preview = state.copy(selectedYear = selectedYear, typeFilter = selectedType, query = jumpToDate.orEmpty())

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
                    state.availableYears.forEach { year ->
                        FilterChipItem(
                            text = year.toString(),
                            selected = year == selectedYear,
                            onClick = { selectedYear = year },
                        )
                    }
                    FilterChipItem(
                        text = stringResource(R.string.filter_older),
                        selected = false,
                        onClick = {
                            selectedYear = state.availableYears.minOrNull() ?: selectedYear
                        },
                    )
                }
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
                        text = jumpToDate ?: stringResource(R.string.filter_jump_to_date),
                        style = MohakashTextStyles.UiSmall,
                        color = if (jumpToDate == null) MohakashColors.TextMuted else MohakashColors.Text,
                    )
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
                    selectedIndex = when (selectedType) {
                        MediaFilter.ALL -> 0
                        MediaFilter.IMAGES -> 1
                        MediaFilter.VIDEOS -> 2
                    },
                    onSelect = { index ->
                        selectedType = when (index) {
                            1 -> MediaFilter.IMAGES
                            2 -> MediaFilter.VIDEOS
                            else -> MediaFilter.ALL
                        }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = stringResource(R.string.filter_reset),
                    onClick = {
                        selectedYear = state.availableYears.firstOrNull() ?: state.selectedYear
                        selectedType = MediaFilter.ALL
                        jumpToDate = null
                        onReset()
                    },
                    modifier = Modifier
                        .width(110.dp)
                        .height(52.dp),
                )
                PrimaryButton(
                    text = pluralStringResource(
                        R.plurals.filter_show_entries,
                        preview.filtered.size,
                        preview.filtered.size,
                    ),
                    onClick = {
                        onYearSelected(selectedYear)
                        onTypeSelected(selectedType)
                        jumpToDate?.let(onJumpToDate)
                        onApply()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = null)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(millis)
                        jumpToDate = formatted
                    }
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
