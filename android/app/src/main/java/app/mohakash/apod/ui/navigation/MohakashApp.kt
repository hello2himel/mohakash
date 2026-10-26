package app.mohakash.apod.ui.navigation

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.archive.ArchiveHubScreen
import app.mohakash.apod.ui.detail.DetailScreen
import app.mohakash.apod.ui.feed.FeedScreen
import app.mohakash.apod.ui.onboarding.OnboardingScreen
import app.mohakash.apod.ui.settings.AdvancedScreen
import app.mohakash.apod.ui.settings.SettingsScreen
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.wallpaper.WallpaperIntentBuilder

/**
 * The navigation graph of the app.
 *
 * The three primary screens (Feed, Archive, Saved) have a bottom navigation bar; the detail screen
 * and the settings screens have a back arrow and no bottom bar. The feed can hide its chrome and
 * the bottom bar with a tap, giving the picture the whole screen.
 */
@Composable
fun MohakashApp(
    startDestination: String,
    deepLinkRequest: DeepLinkRequest? = null,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.TODAY || currentRoute == Routes.ARCHIVE
    var feedFullscreen by remember { mutableStateOf(false) }
    var randomRequest by remember { mutableIntStateOf(0) }
    var latestRequest by remember { mutableIntStateOf(0) }
    var openArchiveOnSaved by remember { mutableStateOf(false) }
    var onboardingCompleted by remember {
        mutableStateOf(startDestination != Routes.ONBOARDING)
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.TODAY) feedFullscreen = false
    }

    // A deep link that arrives while onboarding is showing is kept until the
    // user finishes; a link that arrives later is followed immediately.
    LaunchedEffect(deepLinkRequest, onboardingCompleted) {
        val request = deepLinkRequest ?: return@LaunchedEffect
        if (!onboardingCompleted) return@LaunchedEffect
        navController.navigateTo(request.target)
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            containerColor = MohakashColors.Background,
            // The feed draws its image under the status bar; every top bar and scrollable
            // screen adds its own insets instead.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MohakashColors.SurfaceStrong,
                        contentColor = MohakashColors.Text,
                        actionColor = MohakashColors.Accent,
                    )
                }
            },
            bottomBar = {
                if (showBottomBar && !feedFullscreen) {
                    MohakashBottomBar(
                        currentRoute = currentRoute,
                        onRandom = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            randomRequest += 1
                        },
                        onSelect = { destination ->
                            if (destination == BottomDestination.FEED) latestRequest += 1
                            navController.navigate(destination.route) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            val bottomBarInset = padding.calculateBottomPadding()
            val contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                // The Launch Cradle floats over the screen instead of reserving a solid strip.
                bottom = 0.dp,
            )
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(contentPadding),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinished = {
                            onboardingCompleted = true
                            navController.navigate(Routes.TODAY) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                        onOpenWallpaperPicker = { openWallpaperPicker(context) },
                    )
                }

                composable(Routes.TODAY) {
                    FeedScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenLink = { url -> openLink(context, url) },
                        onOpenSaved = {
                            openArchiveOnSaved = true
                            navController.navigate(Routes.ARCHIVE) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        randomRequest = randomRequest,
                        onRandomRequestConsumed = { randomRequest = 0 },
                        latestRequest = latestRequest,
                        onLatestRequestConsumed = { latestRequest = 0 },
                        onFullscreenChange = { feedFullscreen = it },
                        bottomBarInset = bottomBarInset,
                    )
                }

                composable(Routes.ARCHIVE) {
                    ArchiveHubScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onBrowseFeed = {
                            latestRequest += 1
                            navController.navigate(Routes.TODAY) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        openOnSaved = openArchiveOnSaved,
                        onOpenOnSavedConsumed = { openArchiveOnSaved = false },
                        bottomBarInset = bottomBarInset,
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLink = { url -> openLink(context, url) },
                        onOpenAdvanced = { navController.navigate(Routes.ADVANCED) },
                    )
                }

                composable(Routes.ADVANCED) {
                    AdvancedScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLink = { url -> openLink(context, url) },
                    )
                }

                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("date") { type = NavType.StringType }),
                ) { entry ->
                    val date = entry.arguments?.getString("date").orEmpty()
                    DetailScreen(
                        date = date,
                        onBack = { navController.popBackStack() },
                        onOpenLink = { url -> openLink(context, url) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
            }
        }
    }
}

/**
 * Follows a [DeepLinkTarget].
 *
 * The primary destinations are opened on top of the feed instead of stacking duplicate copies of
 * the bottom-navigation screens, so Back from a deep link returns to the previous context rather
 * than replaying the link.
 */
internal fun NavController.navigateTo(target: DeepLinkTarget) {
    when (target) {
        DeepLinkTarget.Today -> navigate(target.destination) {
            popUpTo(Routes.TODAY) { inclusive = false }
            launchSingleTop = true
        }

        DeepLinkTarget.Archive -> navigate(target.destination) {
            popUpTo(Routes.TODAY) { inclusive = false }
            launchSingleTop = true
            restoreState = true
        }

        is DeepLinkTarget.Detail -> navigate(Routes.detail(target.date)) {
            launchSingleTop = true
        }
    }
}

/** Opens a link in the browser. */
internal fun openLink(context: Context, url: String) {
    if (url.isBlank()) return
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    // API-controlled links must not be able to launch file:, content: or custom schemes.
    if (uri.scheme?.lowercase() !in setOf("https", "http")) return
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        if (it !is ActivityNotFoundException) throw it
    }
}

/**
 * Opens the system preview of the Mohakash live wallpaper.
 *
 * Tries each intent from [WallpaperIntentBuilder.attemptsFor] in turn until one starts
 * successfully.
 *
 * The live-wallpaper component is attached as a real [ComponentName]: the system preview reads
 * `EXTRA_LIVE_WALLPAPER_COMPONENT` with `getParcelableExtra`, and a plain string used to make the
 * wallpaper screen crash on "Preview and set". The service itself is protected by
 * `android.permission.BIND_WALLPAPER`, which is required for the system to bind to it.
 */
internal fun openWallpaperPicker(context: Context) {
    val packageName = context.packageName
    val service = ComponentName(packageName, WallpaperIntentBuilder.SERVICE_CLASS)
    val attempts = WallpaperIntentBuilder.attemptsFor(packageName, directComponentSet = true)
    val fromActivity = context is Activity
    var started = false

    for (spec in attempts) {
        if (started) break
        started = runCatching {
            val intent = Intent(spec.action).apply {
                // An activity context should keep its own task; only a non-activity context needs
                // NEW_TASK. This keeps Back from the preview returning to the launcher.
                if (spec.newTaskFlag && !fromActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                spec.category?.let { addCategory(it) }
                if (spec.attachWallpaperComponent) {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, service)
                }
            }
            // Start directly and let startActivity throw ActivityNotFoundException when no
            // activity matches. resolveActivity(…, 0) rejects some valid implicit intents whose
            // filters declare CATEGORY_DEFAULT, which made the live-wallpaper preview fall back
            // to the display settings on Android 15.
            context.startActivity(intent)
            true
        }.isSuccess
    }
    if (!started) {
        // Last resort: open the device-wide wallpaper settings screen.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    if (!fromActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            // Nothing more we can do; the preview simply isn't available on this device.
        }
    }
}

/** Opens the system settings for this app — used when the wallpaper preview is unavailable. */
internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
