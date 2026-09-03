package com.wayars.app

import android.app.Application
import android.content.Context
import com.wayars.app.data.prefs.LanguagePrefs
import com.wayars.app.util.LocaleManager

class WayArsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun attachBaseContext(base: Context) {
        val languageCode = LanguagePrefs.read(base) ?: LocaleManager.resolveInitialLanguage()
        super.attachBaseContext(LocaleManager.wrap(base, languageCode))
    }
}
