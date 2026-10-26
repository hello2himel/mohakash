package app.mohakash.apod.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Finished -> onFinished()
                OnboardingEvent.OpenWallpaperPicker -> onOpenWallpaperPicker()
                is OnboardingEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    OnboardingContent(
        preview = state.preview,
        isPreparing = state.isPreparing,
        onSetWallpaper = { viewModel.complete(openWallpaperPicker = true) },
        onSkip = { viewModel.complete(openWallpaperPicker = false) },
        modifier = modifier,
    )
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MohakashColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Box(
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
