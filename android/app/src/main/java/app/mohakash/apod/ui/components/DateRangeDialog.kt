package app.mohakash.apod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Whether the date dialog returns one day or an inclusive range. */
enum class DateFilterMode { SINGLE, RANGE }

/**
 * A date picker that can return either one day or a Skyscanner-style inclusive range.
 *
 * Dates cross the dialog boundary as ISO-8601 strings so the rest of the app can compare them
 * without another timezone conversion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    initialFrom: String?,
    initialTo: String?,
    title: String = stringResource(R.string.date_picker_title),
    onDismiss: () -> Unit,
    onConfirm: (from: String, to: String) -> Unit,
) {
    val sameDay = initialFrom != null && initialFrom == initialTo
    var mode by rememberSaveable {
        mutableStateOf(
            if (initialFrom != null && initialTo != null && !sameDay) {
                DateFilterMode.RANGE
            } else {
                DateFilterMode.SINGLE
            },
        )
    }
    val initialMillis = remember(initialFrom, initialTo) {
        parseIsoToUtcMillis(initialFrom ?: initialTo)
    }
    val singleState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val rangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialMillis,
        initialSelectedEndDateMillis = remember(initialFrom, initialTo) {
            parseIsoToUtcMillis(initialTo)
        },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 780.dp)
                .clip(RoundedCornerShape(MohakashDimens.DialogRadius))
                .background(MohakashColors.Surface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MohakashTextStyles.SheetTitle,
                color = MohakashColors.Text,
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.date_picker_single),
                    stringResource(R.string.date_picker_range),
                ),
                selectedIndex = if (mode == DateFilterMode.SINGLE) 0 else 1,
                onSelect = { index -> mode = if (index == 0) DateFilterMode.SINGLE else DateFilterMode.RANGE },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (mode == DateFilterMode.SINGLE) {
                    DatePicker(state = singleState, showModeToggle = false)
                } else {
                    DateRangePicker(state = rangeState, showModeToggle = false)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.clear_cache_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                val canApply = if (mode == DateFilterMode.SINGLE) {
                    singleState.selectedDateMillis != null
                } else {
                    rangeState.selectedStartDateMillis != null && rangeState.selectedEndDateMillis != null
                }
                PrimaryButton(
                    text = stringResource(R.string.filter_confirm),
                    enabled = canApply,
                    onClick = {
                        val (start, end) = if (mode == DateFilterMode.SINGLE) {
                            singleState.selectedDateMillis?.let { it to it }
                        } else {
                            rangeState.selectedStartDateMillis?.let { start ->
                                rangeState.selectedEndDateMillis?.let { end -> start to end }
                            }
                        } ?: return@PrimaryButton
                        onConfirm(isoFromMillis(start), isoFromMillis(end))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** ISO date from a UTC millis value, using the same UTC convention as the APOD archive. */
fun isoFromMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

private fun parseIsoToUtcMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)?.time
    }.getOrNull()
}

/** Human-readable display for an ISO range. */
@Composable
fun dateRangeLabel(from: String?, to: String?, fallback: String): String {
    if (from.isNullOrBlank() && to.isNullOrBlank()) return fallback
    val start = from?.let(::displayIsoDate)
    val end = to?.let(::displayIsoDate)
    return when {
        start == null -> end.orEmpty()
        end == null || start == end -> start
        else -> "$start – $end"
    }
}

private fun displayIsoDate(value: String): String =
    runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value) ?: return value
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(date)
    }.getOrDefault(value)
