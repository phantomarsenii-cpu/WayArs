package com.wayars.app.domain.repository

import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PresetType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val languageCode: Flow<String?>
    val currency: Flow<Currency>
    val preset: Flow<PresetType>
    val onboardingDone: Flow<Boolean>
    val customThresholds: Flow<CustomThresholds?>

    suspend fun setLanguage(code: String)
    suspend fun setCurrency(currency: Currency)
    suspend fun setPreset(preset: PresetType)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setCustomThresholds(bad: Double, average: Double, good: Double)
    suspend fun clearCustomThresholds()
}
