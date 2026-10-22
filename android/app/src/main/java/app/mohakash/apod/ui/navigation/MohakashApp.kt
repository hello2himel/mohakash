package app.mohakash.apod.ui.navigation

import android.content.ActivityNotFoundException
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
                    )
                }

                composable(Routes.ARCHIVE) {
                    ArchiveScreen(
                        onOpenEntry = { date -> navController.navigate(Routes.detail(date)) },
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
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
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
 * successfully. The plain system chooser (`MAIN` + `WallpaperService` category, no extras) is
 * the safe fallback — several OEM pickers crash when unknown extras are attached to the
 * `CHANGE_LIVE_WALLPAPER` intent, which is what crashed the wallpaper menu on "Preview and set".
 */
internal fun openWallpaperPicker(context: Context) {
    val packageName = context.packageName
    val attempts = WallpaperIntentBuilder.attemptsFor(packageName, directComponentSet = true)
    var started = false
    for (spec in attempts) {
        if (started) break
        started = runCatching {
            val intent = Intent(spec.action).apply {
                if (spec.newTaskFlag) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                spec.category?.let { addCategory(it) }
                spec.extras.forEach { (key, value) -> putExtra(key, value) }
            }
            if (context.packageManager.resolveActivity(intent, 0) == null) {
                throw ActivityNotFoundException("No activity resolves ${spec.action}")
            }
            context.startActivity(intent)
            true
        }.isSuccess
    }
    if (!started) {
        // Last resort: open the device-wide wallpaper settings screen.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
