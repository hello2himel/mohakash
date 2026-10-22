package app.mohakash.apod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.mohakash.apod.core.TimeFormatting
import app.mohakash.apod.ui.navigation.MohakashApp
import app.mohakash.apod.ui.navigation.Routes
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTheme
import kotlinx.coroutines.launch

/**
 * The single activity of the app.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MohakashApplication).container

        lifecycleScope.launch {
            // Keep the background job in sync with the preferences and refresh stale metadata.
            val settings = container.settingsStore.current()
            container.syncController.apply(settings)
            if (TimeFormatting.isStale(settings.lastSyncAt, System.currentTimeMillis())) {
                container.syncController.runOnce()
            }
        }

        setContent {
            MohakashTheme {
                var startDestination by remember { mutableStateOf<String?>(null) }
                val resolved = startDestination
                if (resolved == null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MohakashColors.Background),
                    )
                } else {
                    MohakashApp(startDestination = resolved)
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val settings = container.settingsStore.current()
                    startDestination = if (settings.onboardingCompleted) Routes.TODAY else Routes.ONBOARDING
                }
            }
        }
    }
}
