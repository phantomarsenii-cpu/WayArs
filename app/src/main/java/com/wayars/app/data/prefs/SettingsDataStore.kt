package com.wayars.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PresetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wayars_settings")

/**
 * Persists user-level settings: UI language, working currency, active preset,
 * optional custom €/km thresholds, and whether onboarding has been completed.
 * Language is additionally cached synchronously in SharedPreferences (see
 * [LanguagePrefs]) so it can be applied in Activity.attachBaseContext before
 * the first Compose frame.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val CURRENCY = stringPreferencesKey("currency")
        val PRESET = stringPreferencesKey("preset")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CUSTOM_BAD_RATE = doublePreferencesKey("custom_bad_rate")
        val CUSTOM_AVERAGE_RATE = doublePreferencesKey("custom_average_rate")
        val CUSTOM_GOOD_RATE = doublePreferencesKey("custom_good_rate")
    }

    val languageCode: Flow<String?> = context.dataStore.data.map { it[Keys.LANGUAGE] }
    val currency: Flow<Currency> = context.dataStore.data.map { Currency.fromCode(it[Keys.CURRENCY]) }
    val preset: Flow<PresetType> = context.dataStore.data.map {
        it[Keys.PRESET]?.let { name -> runCatching { PresetType.valueOf(name) }.getOrNull() } ?: PresetType.BALANCE
    }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    /** Null unless the user has entered all three of their own rate boundaries. */
    val customThresholds: Flow<CustomThresholds?> = context.dataStore.data.map { prefs ->
        val bad = prefs[Keys.CUSTOM_BAD_RATE]
        val avg = prefs[Keys.CUSTOM_AVERAGE_RATE]
        val good = prefs[Keys.CUSTOM_GOOD_RATE]
        if (bad != null && avg != null && good != null) CustomThresholds(bad, avg, good) else null
    }

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

    suspend fun setCustomThresholds(bad: Double, average: Double, good: Double) {
        context.dataStore.edit {
            it[Keys.CUSTOM_BAD_RATE] = bad
            it[Keys.CUSTOM_AVERAGE_RATE] = average
            it[Keys.CUSTOM_GOOD_RATE] = good
        }
    }

    suspend fun clearCustomThresholds() {
        context.dataStore.edit {
            it.remove(Keys.CUSTOM_BAD_RATE)
            it.remove(Keys.CUSTOM_AVERAGE_RATE)
            it.remove(Keys.CUSTOM_GOOD_RATE)
        }
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
