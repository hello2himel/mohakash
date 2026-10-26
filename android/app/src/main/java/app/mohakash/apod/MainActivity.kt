package app.mohakash.apod

import android.content.Intent
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
import app.mohakash.apod.ui.navigation.DeepLinkParser
import app.mohakash.apod.ui.navigation.DeepLinkRequest
import app.mohakash.apod.ui.navigation.MohakashApp
import app.mohakash.apod.ui.navigation.Routes
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTheme
import kotlinx.coroutines.launch

/**
 * The single activity of the app.
 */
class MainActivity : ComponentActivity() {

    /**
     * The latest archive link opened from the website.
     *
     * A new [DeepLinkRequest] is created for every intent, so opening the same
     * archive URL twice still reaches the navigation graph.
     */
    private var deepLinkRequest by mutableStateOf<DeepLinkRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only consume the launch intent on a fresh start. On a configuration
        // change the original intent is still attached; re-parsing it would
        // reopen an entry after the user has navigated somewhere else.
        if (savedInstanceState == null) {
            deepLinkRequest = DeepLinkParser.parse(intent?.dataString)?.let(::DeepLinkRequest)
        }

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
                    MohakashApp(
                        startDestination = resolved,
                        deepLinkRequest = deepLinkRequest,
                    )
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val settings = container.settingsStore.current()
                    startDestination = if (settings.onboardingCompleted) Routes.TODAY else Routes.ONBOARDING
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkParser.parse(intent.dataString)?.let { deepLinkRequest = DeepLinkRequest(it) }
    }
}
