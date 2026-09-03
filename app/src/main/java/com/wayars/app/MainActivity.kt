package com.wayars.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.wayars.app.data.prefs.LanguagePrefs
import com.wayars.app.presentation.MainViewModel
import com.wayars.app.presentation.ui.navigation.WayArsNavHost
import com.wayars.app.presentation.ui.theme.WayArsTheme
import com.wayars.app.util.LocaleManager
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext.appContainer())
    }

    override fun attachBaseContext(newBase: Context) {
        val languageCode = LanguagePrefs.read(newBase) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(newBase, languageCode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WayArsTheme {
                WayArsNavHost(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onOpenOverlaySettings = { openOverlaySettings() }
                )
            }
        }

        // Language is picked from Settings and persisted via DataStore, but attachBaseContext
        // only runs once per Activity instance — recreate() re-applies it immediately.
        lifecycleScope.launch {
            viewModel.languageCode.filterNotNull().drop(1).collect { recreate() }
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
}
