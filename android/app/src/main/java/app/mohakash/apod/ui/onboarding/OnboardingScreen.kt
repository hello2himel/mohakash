package app.mohakash.apod.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import app.mohakash.apod.wallpaper.WallpaperStatus
import app.mohakash.apod.ui.wallpaper.WallpaperPreview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
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
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.components.QuietButton
import app.mohakash.apod.ui.containerViewModel
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles
import java.io.File

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
            latestImageResolver = container.latestImageResolver,
            syncEngine = container.syncEngine,
            carouselController = container.carouselController,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val appContext = context.applicationContext
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The Settings screen reflects the OS state; denial is not a blocker. */ }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showFrequency by rememberSaveable { mutableStateOf(false) }
    var awaitingPickerReturn by rememberSaveable { mutableStateOf(false) }
    var selectedCadence by rememberSaveable { mutableStateOf(WallpaperCadence.MultipleDaily) }

    // Local, transient guard: the ViewModel also refuses a second completion, this only keeps the
    // button from looking tappable while the finish is in flight.
    var isFinishing by remember { mutableStateOf(false) }
    BackHandler(enabled = showFrequency && !isFinishing) { showFrequency = false }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Finished -> {
                    requestNotificationPermissionIfNeeded()
                    onFinished()
                }
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
            if (WallpaperStatus.isActive(appContext)) {
                showFrequency = true
            } else {
                // The picker was cancelled or never activated Mohakash. Do not turn on the
                // carousel for a wallpaper that is not there; finish with the safe daily mode.
                viewModel.complete(openWallpaperPicker = false)
            }
        }
    }

    if (showFrequency) {
        OnboardingFrequencyContent(
            preview = state.preview,
            selected = selectedCadence,
            onSelect = { selectedCadence = it },
            onBack = { if (!isFinishing) showFrequency = false },
            onContinue = {
                if (!isFinishing) {
                    isFinishing = true
                    viewModel.finishAfterWallpaper(
                        multipleTimesDaily = it == WallpaperCadence.MultipleDaily,
                    )
                }
            },
            modifier = modifier,
            isFinishing = isFinishing,
        )
    } else {
        OnboardingContent(
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
    isPreparing: Boolean,
    onSetWallpaper: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val hidePreview = compactHeight || (largeText && LocalConfiguration.current.screenHeightDp < 640)
    val previewDescription = stringResource(R.string.onboarding_wallpaper_preview)
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
            if (!hidePreview) {
                val stars = remember {
                    val random = kotlin.random.Random(0x0A5A)
                    List(30) {
                        Triple(
                            random.nextFloat(),
                            random.nextFloat(),
                            0.5f + random.nextFloat() * 1.2f,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .aspectRatio(9f / 19f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MohakashColors.Surface)
                        .border(1.dp, MohakashColors.BorderStrong, RoundedCornerShape(28.dp))
                        .semantics {
                            contentDescription = previewDescription
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF080B1C),
                                    Color(0xFF151D46),
                                    Color(0xFF080A16),
                                ),
                            ),
                        )
                        // Soft nebula glow.
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF8FB0FF).copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.24f, size.height * 0.36f),
                                radius = size.width * 0.75f,
                            ),
                            radius = size.width * 0.75f,
                            center = Offset(size.width * 0.24f, size.height * 0.36f),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE0A5C9).copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.78f, size.height * 0.52f),
                                radius = size.width * 0.65f,
                            ),
                            radius = size.width * 0.65f,
                            center = Offset(size.width * 0.78f, size.height * 0.52f),
                        )
                        stars.forEach { (x, y, radius) ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.30f + y * 0.60f),
                                radius = radius.dp.toPx() * 0.55f,
                                center = Offset(x * size.width, y * size.height * 0.82f),
                            )
                        }
                        // A quiet crescent/planet at the bottom, suggesting a sky without
                        // depending on a network image.
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF9FBFFF),
                                    Color(0xFF253366),
                                ),
                                center = Offset(size.width * 0.68f, size.height * 0.76f),
                                radius = size.width * 0.24f,
                            ),
                            radius = size.width * 0.24f,
                            center = Offset(size.width * 0.68f, size.height * 0.76f),
                        )
                        drawCircle(
                            color = Color(0xFF080B1C),
                            radius = size.width * 0.23f,
                            center = Offset(size.width * 0.60f, size.height * 0.74f),
                        )
                    }
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
    preview: WallpaperPreview = WallpaperPreview(),
    selected: WallpaperCadence = WallpaperCadence.MultipleDaily,
    onSelect: (WallpaperCadence) -> Unit = {},
    onBack: () -> Unit = {},
    onContinue: (WallpaperCadence) -> Unit,
    modifier: Modifier = Modifier,
    isFinishing: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val clock = rememberDayCycleClock(animate = !reducedMotion && !isFinishing)
    // Only a local installed file is used inside the previews: no network request is made here.
    val imageModel = preview.localPath?.let(::File)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                text = stringResource(R.string.detail_back),
                onClick = onBack,
                minHeight = 44.dp,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                color = MohakashColors.TextMuted,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_step, 2, 2),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_frequency_title),
                style = MohakashTextStyles.PageTitle,
                color = MohakashColors.Text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.onboarding_frequency_subtitle),
                style = MohakashTextStyles.BodyBase,
                color = MohakashColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedFrequencyCard(
                    title = stringResource(R.string.onboarding_once_daily),
                    hint = stringResource(R.string.onboarding_once_daily_hint),
                    cadence = WallpaperCadence.OnceDaily,
                    clock = clock,
                    imageModel = imageModel,
                    selected = selected == WallpaperCadence.OnceDaily,
                    onClick = { onSelect(WallpaperCadence.OnceDaily) },
                )
                AnimatedFrequencyCard(
                    title = stringResource(R.string.onboarding_multiple_daily),
                    hint = stringResource(R.string.onboarding_multiple_daily_hint),
                    cadence = WallpaperCadence.MultipleDaily,
                    clock = clock,
                    imageModel = imageModel,
                    selected = selected == WallpaperCadence.MultipleDaily,
                    onClick = { onSelect(WallpaperCadence.MultipleDaily) },
                )
            }
        }

        PrimaryButton(
            text = stringResource(R.string.onboarding_frequency_continue),
            onClick = { onContinue(selected) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isFinishing,
            height = 56.dp,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            textStyle = MohakashTextStyles.UiMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun AnimatedFrequencyCard(
    title: String,
    hint: String,
    cadence: WallpaperCadence,
    clock: DayCycleClock,
    imageModel: Any?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val background by animateColorAsState(
        targetValue = if (selected) MohakashColors.SurfaceStrong else MohakashColors.Surface,
        animationSpec = tween(220),
        label = "frequency-card-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MohakashColors.Accent else MohakashColors.BorderStrong,
        animationSpec = tween(220),
        label = "frequency-card-border",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        DayNightPreview(
            clock = clock,
            cadence = cadence,
            imageModel = imageModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
