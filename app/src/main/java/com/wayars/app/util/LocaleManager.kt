package com.wayars.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Supported UI languages. If the device locale isn't in this list on first
 * launch, the app falls back to English, per the spec.
 */
object LocaleManager {

    val supported = listOf(
        "en", "pl", "ro", "uk", "ru", "de", "fr",
        // Added alongside the CIS/European currencies.
        "uz", "ka", "hy", "az", "cs", "hu", "bg", "sv", "nb", "da"
    )
    const val fallback = "en"

    fun resolveInitialLanguage(): String {
        val deviceLang = Locale.getDefault().language
        // Norwegian devices commonly report "no" rather than "nb" (Bokmål) —
        // treat them the same since "nb" is the variant actually shipped.
        val normalized = if (deviceLang == "no") "nb" else deviceLang
        return if (supported.contains(normalized)) normalized else fallback
    }

    /** Wraps [base] with a Configuration forced to [languageCode]. Call from attachBaseContext. */
    fun wrap(base: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun displayName(code: String): String = when (code) {
        "en" -> "English"
        "pl" -> "Polski"
        "ro" -> "Română"
        "uk" -> "Українська"
        "ru" -> "Русский"
        "de" -> "Deutsch"
        "fr" -> "Français"
        "uz" -> "Oʻzbekcha"
        "ka" -> "ქართული"
        "hy" -> "Հայերեն"
        "az" -> "Azərbaycanca"
        "cs" -> "Čeština"
        "hu" -> "Magyar"
        "bg" -> "Български"
        "sv" -> "Svenska"
        "nb" -> "Norsk bokmål"
        "da" -> "Dansk"
        else -> code
    }
}
