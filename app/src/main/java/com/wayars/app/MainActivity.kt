package com.wayars.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.wayars.app.data.prefs.LanguagePrefs
import com.wayars.app.presentation.MainViewModel
import com.wayars.app.presentation.ui.navigation.WayArsNavHost
import com.wayars.app.presentation.ui.theme.WayArsTheme
import com.wayars.app.util.LocaleManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext.appContainer())
    }

    // The language actually baked into this Activity instance's resources by
    // attachBaseContext. Compared against live settings changes below —
    // this replaces a fragile "drop the first Flow emission" heuristic that
    // could race with DataStore's initial load and skip a real language
    // change (reported as "switching language doesn't always work").
    private var appliedLanguage: String = LocaleManager.fallback

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguagePrefs.read(newBase) ?: LocaleManager.resolveInitialLanguage()
        appliedLanguage = languageCode
        super.attachBaseContext(LocaleManager.wrap(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // enableEdgeToEdge() makes the system bars transparent by design and
        // draws app content behind them — setting window.navigationBarColor
        // /statusBarColor afterwards fights that (and behaves inconsistently
        // across Android versions, which is exactly why a mismatched dark
        // strip kept showing above the nav bar on some devices no matter what
        // color was set there). The correct fix is to let our OWN Compose
        // background paint that area instead of trying to color the system
        // bar directly — see WayArsNavHost's root Surface.
        enableEdgeToEdge()

        setContent {
            WayArsTheme {
                WayArsNavHost(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onOpenOverlaySettings = { openOverlaySettings() },
                    onOpenNotificationSettings = { openNotificationSettings() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.languageCode.filterNotNull().collect { code ->
                if (code != appliedLanguage) recreate()
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
