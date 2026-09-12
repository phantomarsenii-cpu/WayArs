package com.wayars.app.presentation

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesTransactionException
import com.wayars.app.AppContainer
import com.wayars.app.billing.RevenueCatConfig
import com.wayars.app.domain.model.SubscriptionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row on the paywall — a RevenueCat [Package] plus which card it renders as. */
data class PlanOption(
    val plan: PlanType,
    val rcPackage: Package
)

enum class PlanType { WEEKLY, MONTHLY, YEARLY }

data class PaywallUiState(
    val isLoadingOfferings: Boolean = true,
    val plans: List<PlanOption> = emptyList(),
    val isPurchasing: Boolean = false,
    val errorMessage: String? = null
)

class SubscriptionViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.subscriptionRepository

    /** Drives the splash → paywall / main routing decision. */
    val gateState: StateFlow<SubscriptionState> =
        repository.subscriptionState.stateIn(viewModelScope, SharingStarted.Eagerly, SubscriptionState.Loading)

    private val _paywallState = MutableStateFlow(PaywallUiState())
    val paywallState: StateFlow<PaywallUiState> = _paywallState.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshCustomerInfo() }
    }

    /** Re-runs the CustomerInfo check, e.g. after the user taps "retry" on an offline error. */
    fun retryGateCheck() {
        viewModelScope.launch { repository.refreshCustomerInfo() }
    }

    fun loadOfferings() {
        viewModelScope.launch {
            _paywallState.value = _paywallState.value.copy(isLoadingOfferings = true, errorMessage = null)
            repository.getOfferings()
                .onSuccess { offerings ->
                    val offering: Offering? = offerings.current
                    val plans = listOfNotNull(
                        offering?.availablePackages
                            ?.find { it.identifier == RevenueCatConfig.PackageIds.WEEKLY }
                            ?.let { PlanOption(PlanType.WEEKLY, it) },
                        offering?.availablePackages
                            ?.find { it.identifier == RevenueCatConfig.PackageIds.MONTHLY }
                            ?.let { PlanOption(PlanType.MONTHLY, it) },
                        offering?.availablePackages
                            ?.find { it.identifier == RevenueCatConfig.PackageIds.YEARLY }
                            ?.let { PlanOption(PlanType.YEARLY, it) }
                    )
                    _paywallState.value = _paywallState.value.copy(
                        isLoadingOfferings = false,
                        plans = plans,
                        errorMessage = if (plans.isEmpty()) "No plans available right now." else null
                    )
                }
                .onFailure { error ->
                    _paywallState.value = _paywallState.value.copy(
                        isLoadingOfferings = false,
                        errorMessage = error.message ?: "Couldn't load plans."
                    )
                }
        }
    }

    fun purchase(activity: Activity, packageToPurchase: Package) {
        viewModelScope.launch {
            _paywallState.value = _paywallState.value.copy(isPurchasing = true, errorMessage = null)
            repository.purchase(activity, packageToPurchase)
                .onSuccess {
                    _paywallState.value = _paywallState.value.copy(isPurchasing = false)
                    // gateState updates on its own via repository.subscriptionState —
                    // the nav layer reacts to that, nothing else to do here.
                }
                .onFailure { error ->
                    _paywallState.value = _paywallState.value.copy(
                        isPurchasing = false,
                        errorMessage = purchaseErrorMessage(error)
                    )
                }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _paywallState.value = _paywallState.value.copy(isPurchasing = true, errorMessage = null)
            repository.restore()
                .onSuccess {
                    _paywallState.value = _paywallState.value.copy(isPurchasing = false)
                }
                .onFailure { error ->
                    _paywallState.value = _paywallState.value.copy(
                        isPurchasing = false,
                        errorMessage = error.message ?: "Restore failed."
                    )
                }
        }
    }

    fun clearError() {
        _paywallState.value = _paywallState.value.copy(errorMessage = null)
    }

    private fun purchaseErrorMessage(error: Throwable): String? {
        val transactionException = error as? PurchasesTransactionException
        return when {
            transactionException == null -> error.message ?: "Purchase failed."
            transactionException.userCancelled -> null // user backed out — not an error to show
            transactionException.error.code == PurchasesErrorCode.PaymentPendingError ->
                "Your payment is pending approval."
            transactionException.error.code == PurchasesErrorCode.ProductAlreadyPurchasedError ->
                "You already own this subscription — try Restore Purchases."
            transactionException.error.code == PurchasesErrorCode.NetworkError ->
                "Network error — check your connection and try again."
            else -> transactionException.error.message
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SubscriptionViewModel(container) as T
    }
}
