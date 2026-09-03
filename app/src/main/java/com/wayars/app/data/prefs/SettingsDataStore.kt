package com.wayars.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PresetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wayars_settings")

/**
 * Persists user-level settings: UI language, working currency, active preset,
 * and whether onboarding has been completed. Language is additionally cached
 * synchronously in SharedPreferences (see [LanguagePrefs]) so it can be applied
 * in Activity.attachBaseContext before the first Compose frame.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val CURRENCY = stringPreferencesKey("currency")
        val PRESET = stringPreferencesKey("preset")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val languageCode: Flow<String?> = context.dataStore.data.map { it[Keys.LANGUAGE] }
    val currency: Flow<Currency> = context.dataStore.data.map { Currency.fromCode(it[Keys.CURRENCY]) }
    val preset: Flow<PresetType> = context.dataStore.data.map {
        it[Keys.PRESET]?.let { name -> runCatching { PresetType.valueOf(name) }.getOrNull() } ?: PresetType.BALANCE
    }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
        LanguagePrefs.save(context, code)
    }

    suspend fun setCurrency(currency: Currency) {
        context.dataStore.edit { it[Keys.CURRENCY] = currency.code }
    }

    suspend fun setPreset(preset: PresetType) {
        context.dataStore.edit { it[Keys.PRESET] = preset.name }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
}

/**
 * Tiny synchronous cache for the selected language, used only inside
 * attachBaseContext (where suspending DataStore reads are not viable).
 * The DataStore Flow above remains the source of truth for the rest of the app.
 */
object LanguagePrefs {
    private const val PREFS = "wayars_lang_cache"
    private const val KEY = "language"

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).apply()
    }

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
