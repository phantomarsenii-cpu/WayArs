package com.wayars.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wayars.app.AppContainer
import com.wayars.app.domain.model.Currency
import com.wayars.app.domain.model.OrderEvaluation
import com.wayars.app.domain.model.PresetType
import com.wayars.app.domain.repository.OrderRecord
import com.wayars.app.presentation.widget.OverlayState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodaySummary(
    val ordersCount: Int = 0,
    val totalEarnings: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val totalTimeMinutes: Double = 0.0,
    val avgRatePerKm: Double = 0.0,
    val currency: Currency = Currency.default
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository

    val languageCode: StateFlow<String?> =
        settings.languageCode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currency: StateFlow<Currency> =
        settings.currency.stateIn(viewModelScope, SharingStarted.Eagerly, Currency.default)

    val preset: StateFlow<PresetType> =
        settings.preset.stateIn(viewModelScope, SharingStarted.Eagerly, PresetType.BALANCE)

    val onboardingDone: StateFlow<Boolean> =
        settings.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val todayOrders: StateFlow<List<OrderRecord>> =
        container.orderRepository.observeToday().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todaySummary: StateFlow<TodaySummary> = todayOrders
        .map { orders -> buildSummary(orders) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TodaySummary())

    val latestEvaluation: StateFlow<OrderEvaluation?> = OverlayState.latestEvaluation

    private fun buildSummary(orders: List<OrderRecord>): TodaySummary {
        if (orders.isEmpty()) return TodaySummary(currency = currency.value)
        val totalEarnings = orders.sumOf { it.evaluation.earnings }
        val totalDistance = orders.sumOf { it.evaluation.distanceKm }
        val totalTime = orders.sumOf { it.evaluation.timeMinutes }
        return TodaySummary(
            ordersCount = orders.size,
            totalEarnings = totalEarnings,
            totalDistanceKm = totalDistance,
            totalTimeMinutes = totalTime,
            avgRatePerKm = if (totalDistance > 0) totalEarnings / totalDistance else 0.0,
            currency = orders.first().evaluation.currency
        )
    }

    fun setLanguage(code: String) = viewModelScope.launch { settings.setLanguage(code) }
    fun setCurrency(currency: Currency) = viewModelScope.launch { settings.setCurrency(currency) }
    fun setPreset(preset: PresetType) = viewModelScope.launch { settings.setPreset(preset) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboardingDone(true) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
