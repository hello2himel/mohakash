package app.mohakash.apod.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.QuietButton
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import coil.compose.AsyncImage

/**
 * The screen that is shown once, right after the installation.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = containerViewModel { container ->
        OnboardingViewModel(
            settingsStore = container.settingsStore,
            syncController = container.syncController,
            wallpaperStorage = container.wallpaperStorage,
            apodRepository = container.apodRepository,
            syncEngine = container.syncEngine,
            carouselController = container.carouselController,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    var showFrequency by rememberSaveable { mutableStateOf(false) }
    var awaitingPickerReturn by rememberSaveable { mutableStateOf(false) }
    // Local, transient guard: the ViewModel also refuses a second completion, this only keeps the
    // button from looking tappable while the finish is in flight.
    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Finished -> onFinished()
                OnboardingEvent.OpenWallpaperPicker -> {
                    awaitingPickerReturn = true
                    onOpenWallpaperPicker()
                }
                is OnboardingEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // The wallpaper preview activity has no result intent; ON_RESUME is the reliable signal
    // that the system picker has closed and the frequency page can be shown.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPickerReturn) {
            awaitingPickerReturn = false
            showFrequency = true
        }
    }

    if (showFrequency) {
        OnboardingFrequencyContent(
            onContinue = { multipleTimesDaily ->
                if (!isFinishing) {
                    isFinishing = true
                    viewModel.finishAfterWallpaper(multipleTimesDaily = multipleTimesDaily)
                }
            },
            modifier = modifier,
            isFinishing = isFinishing,
        )
    } else {
        OnboardingContent(
            preview = state.preview,
            isPreparing = state.isPreparing,
            onSetWallpaper = { viewModel.complete(openWallpaperPicker = true) },
            onSkip = { viewModel.complete(openWallpaperPicker = false) },
            modifier = modifier,
        )
    }
}

/** The stateless onboarding screen, used by the previews as well. */
@Composable
internal fun OnboardingContent(
    preview: WallpaperPreview,
    isPreparing: Boolean,
    onSetWallpaper: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val hidePreview = compactHeight || (largeText && LocalConfiguration.current.screenHeightDp < 640)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!hidePreview) Box(
                modifier = Modifier
                    .width(180.dp)
                    .aspectRatio(9f / 19f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MohakashColors.Surface)
                    .border(1.dp, MohakashColors.BorderStrong, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (preview.model != null) {
                    AsyncImage(
                        model = preview.model,
                        contentDescription = stringResource(R.string.onboarding_wallpaper_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.onboarding_wallpaper_preview),
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_headline),
                    style = MohakashTextStyles.OnboardingTitle,
                    color = MohakashColors.Text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    style = MohakashTextStyles.BodyBase,
                    color = MohakashColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(R.string.onboarding_set_wallpaper),
                onClick = onSetWallpaper,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPreparing,
                height = 52.dp,
                textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            // A text button: no border, no fill — exactly as on the first-launch design.
            QuietButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp,
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
            )
            Text(
                text = stringResource(R.string.onboarding_wifi_note),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}



/**
 * Page two, after the system wallpaper picker: choose how often the wallpaper changes.
 *
 * The choice is local until [onContinue] is pressed — tapping an option never finishes setup on
 * its own. "A few times a day" (the carousel) is the default.
 *
 * The options live in the scrollable part of the page so they never overflow on short screens;
 * the Continue button and its helper stay pinned below and are always reachable.
 */
@Composable
internal fun OnboardingFrequencyContent(
    onContinue: (multipleTimesDaily: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isFinishing: Boolean = false,
) {
    var multipleTimesDaily by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_frequency_title),
                style = MohakashTextStyles.PageTitle,
                color = MohakashColors.Text,
                modifier = Modifier.semantics { heading() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FrequencyOption(
                    title = stringResource(R.string.onboarding_once_daily),
                    hint = stringResource(R.string.onboarding_once_daily_hint),
                    visual = FrequencyVisual.Daily,
                    selected = !multipleTimesDaily,
                    onClick = { multipleTimesDaily = false },
                )
                FrequencyOption(
                    title = stringResource(R.string.onboarding_multiple_daily),
                    hint = stringResource(R.string.onboarding_multiple_daily_hint),
                    visual = FrequencyVisual.Rhythm,
                    selected = multipleTimesDaily,
                    onClick = { multipleTimesDaily = true },
                )
            }
        }

        PrimaryButton(
            text = stringResource(R.string.onboarding_frequency_continue),
            onClick = { onContinue(multipleTimesDaily) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFinishing,
            height = 56.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = stringResource(R.string.onboarding_frequency_hint),
            style = MohakashTextStyles.Helper,
            color = MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

/** The two pictograms of the frequency cards. Both are drawn from existing app resources. */
private enum class FrequencyVisual { Daily, Rhythm }

/**
 * A large, whole-card radio option.
 *
 * The card is the touch target (at least 88 dp high) and carries [Role.RadioButton] plus the
 * selected state, so TalkBack reads label + helper + selected in one announcement. The pictogram
 * and the radio marker are decorative; the marker is the visual counterpart of that semantics.
 */
@Composable
private fun FrequencyOption(
    title: String,
    hint: String,
    visual: FrequencyVisual,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val contentColor = if (selected) MohakashColors.Accent else MohakashColors.TextMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(if (selected) MohakashColors.SurfaceStrong else MohakashColors.Surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MohakashColors.Accent else MohakashColors.Border,
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (selected) {
                        MohakashColors.Accent.copy(alpha = 0.14f)
                    } else {
                        MohakashColors.SurfaceStrong
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (visual) {
                FrequencyVisual.Daily -> Icon(
                    painter = painterResource(R.drawable.ic_today_24),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )

                FrequencyVisual.Rhythm -> RhythmGlyph(tint = contentColor)
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MohakashColors.Text,
            )
            Text(
                text = hint,
                style = MohakashTextStyles.Helper,
                color = if (selected) MohakashColors.TextBody else MohakashColors.TextMuted,
            )
        }
        SelectionMarker(selected = selected)
    }
}

/** Three dots on a line — a small rhythm of changes across the day. */
@Composable
private fun RhythmGlyph(tint: Color) {
    Row(
        modifier = Modifier.width(26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(tint, CircleShape),
            )
            if (index < 2) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(tint.copy(alpha = 0.45f)),
                )
            }
        }
    }
}

/** The radio marker: an accent ring with a filled centre when selected. Decorative. */
@Composable
private fun SelectionMarker(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(
                width = 2.dp,
                color = if (selected) MohakashColors.Accent else MohakashColors.BorderStrong,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MohakashColors.Accent, CircleShape),
            )
        }
    }
}
