package app.mohakash.apod.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.BuildConfig
import app.mohakash.apod.R
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.InfoRow
import app.mohakash.apod.ui.components.LinkRow
import app.mohakash.apod.ui.components.MohakashTopBar
import app.mohakash.apod.ui.components.NoteBox
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.SecondaryButton
import app.mohakash.apod.ui.components.SectionLabel
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The advanced settings screen.
 *
 * The only setting here is the API base URL. Changing it clears cached metadata and schedules a
 * refresh so the rest of the app immediately starts using the new endpoint.
 */
@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        AdvancedViewModel(
            settingsStore = container.settingsStore,
            apodRepository = container.apodRepository,
            archiveIndexRepository = container.archiveIndexRepository,
            syncController = container.syncController,
            defaultUrl = container.baseUrlResolver.defaultBaseUrl.orEmpty(),
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    val savedMessage = stringResource(R.string.settings_advanced_saved)
    val resetMessage = stringResource(R.string.settings_advanced_reset_done)
    val alreadyDefaultMessage = stringResource(R.string.settings_advanced_already_default)
    val alreadyUsingMessage = stringResource(R.string.settings_advanced_already_using)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                AdvancedEvent.Saved -> savedMessage
                AdvancedEvent.Reset -> resetMessage
                AdvancedEvent.AlreadyDefault -> alreadyDefaultMessage
                AdvancedEvent.AlreadyUsing -> alreadyUsingMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    AdvancedContent(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::setInput,
        onSave = viewModel::save,
        onReset = viewModel::reset,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}

/** The stateless advanced screen, used by the previews as well. */
@Composable
internal fun AdvancedContent(
    state: AdvancedUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background),
    ) {
        MohakashTopBar(
            title = stringResource(R.string.settings_advanced_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = MohakashDimens.SidePadding)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_advanced_field_label))

            Text(
                text = stringResource(R.string.settings_advanced_intro),
                style = MohakashTextStyles.BodySmall,
                color = MohakashColors.TextBody,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            EndpointField(
                value = state.input,
                onValueChange = onInputChange,
                placeholder = state.defaultUrl,
                label = stringResource(R.string.settings_advanced_field_label),
                isError = state.error == AdvancedError.INVALID_URL,
            )

            Text(
                text = stringResource(
                    when (state.error) {
                        AdvancedError.INVALID_URL -> R.string.settings_advanced_invalid
                        AdvancedError.SAVE_FAILED -> R.string.settings_advanced_save_failed
                        null -> R.string.settings_advanced_field_helper
                    },
                ),
                style = MohakashTextStyles.Helper,
                color = if (state.error == null) MohakashColors.TextMuted else MohakashColors.Error,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    text = stringResource(
                        if (state.isSaving) R.string.settings_advanced_saving else R.string.settings_advanced_save,
                    ),
                    onClick = onSave,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                )
                if (state.isCustom) {
                    SecondaryButton(
                        text = stringResource(R.string.settings_advanced_reset),
                        onClick = onReset,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionLabel(
                text = stringResource(R.string.settings_advanced_current),
                modifier = Modifier.padding(top = 28.dp),
            )

            InfoRow(
                title = stringResource(R.string.settings_advanced_status),
                value = stringResource(
                    if (state.isCustom) {
                        R.string.settings_api_endpoint_custom
                    } else {
                        R.string.settings_api_endpoint_default
                    },
                ),
                showDivider = false,
            )

            Text(
                text = state.savedCustomUrl ?: state.defaultUrl,
                style = MohakashTextStyles.Helper,
                color = MohakashColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )

            NoteBox(
                text = stringResource(R.string.settings_advanced_clear_note),
                modifier = Modifier.padding(top = 16.dp),
            )

            SectionLabel(
                text = stringResource(R.string.settings_advanced_section_guides),
                modifier = Modifier.padding(top = 28.dp),
            )

            LinkRow(
                text = stringResource(R.string.settings_advanced_api_docs),
                onClick = { onOpenLink("${BuildConfig.WEBSITE_BASE_URL}/api") },
                showDivider = false,
            )
            LinkRow(
                text = stringResource(R.string.settings_advanced_api_reference),
                onClick = { onOpenLink("${BuildConfig.REPOSITORY_URL}#8-public-api") },
            )
            LinkRow(
                text = stringResource(R.string.settings_advanced_latest_example),
                onClick = {
                    val baseUrl = state.savedCustomUrl ?: state.defaultUrl
                    if (baseUrl.isNotBlank()) {
                        onOpenLink("$baseUrl/latest.json")
                    }
                },
            )

            NoteBox(
                text = stringResource(R.string.settings_advanced_format_note),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** A single-line URL field with the app's own surface and border styling. */
@Composable
private fun EndpointField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(MohakashDimens.CardRadius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MohakashTextStyles.BodyMedium.copy(color = MohakashColors.Text),
        cursorBrush = SolidColor(MohakashColors.Accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(MohakashColors.Surface)
                    .border(
                        width = 1.dp,
                        color = if (isError) MohakashColors.Error else MohakashColors.Border,
                        shape = shape,
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MohakashTextStyles.BodyMedium,
                            color = MohakashColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
