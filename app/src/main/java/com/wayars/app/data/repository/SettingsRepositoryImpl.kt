package com.wayars.app.data.repository

import com.wayars.app.data.prefs.SettingsDataStore
import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(private val store: SettingsDataStore) : SettingsRepository {
    override val languageCode: Flow<String?> = store.languageCode
    override val currency: Flow<Currency> = store.currency
    override val preset: Flow<PresetType> = store.preset
    override val onboardingDone: Flow<Boolean> = store.onboardingDone
    override val customThresholds: Flow<CustomThresholds?> = store.customThresholds

    override suspend fun setLanguage(code: String) = store.setLanguage(code)
    override suspend fun setCurrency(currency: Currency) = store.setCurrency(currency)
    override suspend fun setPreset(preset: PresetType) = store.setPreset(preset)
    override suspend fun setOnboardingDone(done: Boolean) = store.setOnboardingDone(done)
    override suspend fun setCustomThresholds(bad: Double, average: Double, good: Double) =
        store.setCustomThresholds(bad, average, good)
    override suspend fun clearCustomThresholds() = store.clearCustomThresholds()
}
