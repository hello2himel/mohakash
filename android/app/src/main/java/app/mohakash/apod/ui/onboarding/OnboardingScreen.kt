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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val hidePreview = compactHeight || largeText
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
    ) {
        if (!hidePreview) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // The phone gets exactly the height left after the copy and actions below.
                // Its width follows the 9:19 phone ratio, so it never distorts or overflows.
                val maxPhoneHeight = 180.dp * 19f / 9f
                val phoneHeight = minOf(maxHeight, maxPhoneHeight)
                if (phoneHeight >= 160.dp) {
                    val phoneWidth = phoneHeight * 9f / 19f
                    OnboardingPhoneMockup(
                        modifier = Modifier
                            .width(phoneWidth)
                            .height(phoneHeight),
                    )
                }
            }
            OnboardingCopy()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OnboardingCopy()
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
            QuietButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp,
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
            )
        }
    }
}

@Composable
private fun OnboardingCopy(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 28.dp, bottom = 20.dp),
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

@Composable
private fun OnboardingPhoneMockup(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = kotlin.random.Random(0x0A5A)
        List(52) {
            Triple(
                random.nextFloat(),
                random.nextFloat(),
                0.45f + random.nextFloat() * 1.35f,
            )
        }
    }
    val previewDescription = stringResource(R.string.onboarding_wallpaper_preview)
    val phoneShape = RoundedCornerShape(30.dp)
    val screenShape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = phoneShape,
                clip = false,
                ambientColor = MohakashColors.Accent.copy(alpha = 0.25f),
                spotColor = MohakashColors.Accent.copy(alpha = 0.35f),
            )
            .clip(phoneShape)
            .background(Color(0xFF070A12))
            .border(1.5.dp, Color.White.copy(alpha = 0.18f), phoneShape)
            .semantics {
                contentDescription = previewDescription
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(screenShape)
                .background(Color(0xFF05070F))
                .border(0.6.dp, Color.White.copy(alpha = 0.10f), screenShape),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF070A1C),
                            Color(0xFF141B4A),
                            Color(0xFF070812),
                        ),
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF8FB0FF).copy(alpha = 0.32f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.26f, size.height * 0.32f),
                        radius = size.width * 0.72f,
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.26f, size.height * 0.32f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE0A5C9).copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.36f),
                        radius = size.width * 0.58f,
                    ),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.78f, size.height * 0.36f),
                )
                stars.forEach { (x, y, radius) ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f + y * 0.7f),
                        radius = radius.dp.toPx() * 0.55f,
                        center = Offset(x * size.width, y * size.height * 0.62f),
                    )
                }
                val center = Offset(size.width * 0.56f, size.height * 0.27f)
                val radius = size.width * 0.18f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF3D8A8),
                            Color(0xFFB87C58),
                            Color(0xFF3B2845),
                        ),
                        center = Offset(center.x - radius * 0.25f, center.y - radius * 0.30f),
                        radius = radius * 1.8f,
                    ),
                    radius = radius,
                    center = center,
                )
                drawOval(
                    color = Color(0xFFE9C78B).copy(alpha = 0.68f),
                    topLeft = Offset(center.x - radius * 1.55f, center.y - radius * 0.15f),
                    size = Size(radius * 3.1f, radius * 0.30f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.09f),
                )
                drawCircle(
                    color = Color(0xFF05070F).copy(alpha = 0.30f),
                    radius = radius * 0.92f,
                    center = Offset(center.x + radius * 0.34f, center.y - radius * 0.16f),
                )
            }
            PhoneLauncherChrome(Modifier.matchParentSize())
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
                    selected = selected == WallpaperCadence.OnceDaily,
                    onClick = { onSelect(WallpaperCadence.OnceDaily) },
                )
                AnimatedFrequencyCard(
                    title = stringResource(R.string.onboarding_multiple_daily),
                    hint = stringResource(R.string.onboarding_multiple_daily_hint),
                    cadence = WallpaperCadence.MultipleDaily,
                    clock = clock,
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
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
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
