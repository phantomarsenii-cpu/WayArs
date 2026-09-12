package com.wayars.app.domain.repository

import com.wayars.app.domain.model.CustomThresholds
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.PackageHint
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.model.VehicleProfile
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val languageCode: Flow<String?>
    val currency: Flow<Currency>
    val preset: Flow<PresetType>
    val onboardingDone: Flow<Boolean>
    val customThresholds: Flow<CustomThresholds?>
    val vehicleProfile: Flow<VehicleProfile>
    val customPackages: Flow<Set<String>>
    val packageHints: Flow<Map<String, PackageHint>>

    suspend fun setLanguage(code: String)
    suspend fun setCurrency(currency: Currency)
    suspend fun setPreset(preset: PresetType)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setCustomThresholds(bad: Double, average: Double, good: Double)
    suspend fun clearCustomThresholds()
    suspend fun setVehicleProfile(profile: VehicleProfile)
    suspend fun addCustomPackage(packageName: String)
    suspend fun removeCustomPackage(packageName: String)
    suspend fun savePackageHint(hint: PackageHint)
    suspend fun clearPackageHint(packageName: String)
}
