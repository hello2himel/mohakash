package app.mohakash.apod.ui.navigation

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mohakash.apod.ui.LocalSnackbarHostState
import app.mohakash.apod.ui.archive.ArchiveScreen
import app.mohakash.apod.ui.detail.DetailScreen
import app.mohakash.apod.ui.onboarding.OnboardingScreen
import app.mohakash.apod.ui.saved.SavedScreen
import app.mohakash.apod.ui.settings.AdvancedScreen
import app.mohakash.apod.ui.settings.SettingsScreen
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.today.TodayScreen
import app.mohakash.apod.wallpaper.WallpaperIntentBuilder

/**
 * The navigation graph of the app.
 *
 * The three primary screens have a bottom navigation bar; the detail screen and the settings
 * screen have a back arrow and no bottom bar.
 */
@Composable
fun MohakashApp(
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.TODAY ||
        currentRoute == Routes.ARCHIVE ||
        currentRoute == Routes.SAVED

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            modifier = modifier,
            containerColor = MohakashColors.Background,
            contentWindowInsets = WindowInsets.systemBars,
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
                if (showBottomBar) {
                    MohakashBottomBar(
                        currentRoute = currentRoute,
                        onSelect = { destination ->
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
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinished = {
                            navController.navigate(Routes.TODAY) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                        onOpenWallpaperPicker = { openWallpaperPicker(context) },
                    )
                }

                composable(Routes.TODAY) {
                    TodayScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenArchive = {
                            navController.navigate(Routes.ARCHIVE) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable(Routes.ARCHIVE) {
                    ArchiveScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(Routes.SAVED) {
                    SavedScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
                        onBrowseArchive = {
                            navController.navigate(Routes.ARCHIVE) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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

/** Opens a link in the browser. */
internal fun openLink(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
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
