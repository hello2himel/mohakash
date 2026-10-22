package app.mohakash.apod.ui.archive

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.FilterChipItem
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SegmentedControl
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The first and the last year offered by the *Older* year picker. */
private const val OLDEST_YEAR = 1995
private const val NEWEST_OLDER_YEAR = 2020

/** How many year chips the filter sheet shows before *Older*. */
private const val QUICK_YEAR_COUNT = 6

/**
 * The filter sheet of the archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        FilterSheetContent(
            state = state,
            onYearSelected = onYearSelected,
            onTypeSelected = onTypeSelected,
            onJumpToDate = onJumpToDate,
            onReset = onReset,
            onApply = onApply,
        )
    }
}

/** The stateless body of the filter sheet — usable in a preview without the modal container. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheetContent(
    state: ArchiveUiState,
    onYearSelected: (Int) -> Unit,
    onTypeSelected: (MediaFilter) -> Unit,
    onJumpToDate: (String) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedYear by remember { mutableStateOf(state.selectedYear) }
    var selectedType by remember { mutableStateOf(state.typeFilter) }
    var jumpToDate by remember { mutableStateOf(state.jumpToDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    val preview = state.copy(selectedYear = selectedYear, typeFilter = selectedType, query = jumpToDate.orEmpty())
    val olderSelected = state.availableYears.isNotEmpty() && selectedYear !in state.availableYears
    val defaultYear = ArchiveUiState.defaultYear().let { current ->
        if (current in state.availableYears) current else state.availableYears.firstOrNull() ?: current
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The sheet scrolls when a very large font scale makes it taller than the screen.
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
                state.availableYears.take(QUICK_YEAR_COUNT).forEach { year ->
                    FilterChipItem(
                        text = year.toString(),
                        selected = year == selectedYear,
                        onClick = { selectedYear = year },
                    )
                }
                FilterChipItem(
                    text = stringResource(R.string.filter_older),
                    selected = olderSelected,
                    onClick = { showYearPicker = true },
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
                    text = jumpToDate ?: stringResource(R.string.filter_jump_to_date_placeholder),
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
                    selectedYear = defaultYear
                    selectedType = MediaFilter.ALL
                    jumpToDate = null
                    onReset()
                },
                modifier = Modifier.width(110.dp),
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium,
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
                modifier = Modifier.weight(1f),
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
            )
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

    if (showYearPicker) {
        YearPickerDialog(
            selectedYear = selectedYear,
            onDismiss = { showYearPicker = false },
            onYearSelected = { year ->
                selectedYear = year
                showYearPicker = false
            },
        )
    }
}

/** The 1995–2020 year picker opened by the *Older* chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearPickerDialog(
    selectedYear: Int,
    onDismiss: () -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(MohakashDimens.DialogRadius)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MohakashColors.Surface)
                .border(1.dp, MohakashColors.Border, shape)
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.filter_year_picker_title),
                    style = MohakashTextStyles.SheetTitle,
                    color = MohakashColors.Text,
                )
                FlowRow(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
                    verticalArrangement = Arrangement.spacedBy(MohakashDimens.ChipGap),
                ) {
                    (NEWEST_OLDER_YEAR downTo OLDEST_YEAR).forEach { year ->
                        FilterChipItem(
                            text = year.toString(),
                            selected = year == selectedYear,
                            onClick = { onYearSelected(year) },
                        )
                    }
                }
                TextAction(
                    text = stringResource(R.string.filter_close),
                    onClick = onDismiss,
                    color = MohakashColors.Accent,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
