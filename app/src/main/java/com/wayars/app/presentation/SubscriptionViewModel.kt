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

/**
 * One row on the paywall.
 *
 * [rcPackage] is null until RevenueCat has actually returned a matching
 * [Package] for this plan — e.g. offline, no network yet, or the products
 * haven't been created in Play Console/RevenueCat. In that state the card
 * still renders with [priceText]/[originalPriceText] (hardcoded fallback
 * copy, see [MockPlans]) so the paywall never looks broken or empty, but
 * the plan can't actually be purchased yet ([isLive] is false).
 *
 * As soon as [SubscriptionViewModel.loadOfferings] succeeds, the matching
 * mock row is swapped for one built from the real [Package] — real price,
 * real currency, purchasable — with no visible layout change.
 */
data class PlanOption(
    val plan: PlanType,
    val priceText: String,
    val originalPriceText: String? = null,
    val rcPackage: Package? = null
) {
    val isLive: Boolean get() = rcPackage != null
}

enum class PlanType { WEEKLY, MONTHLY, YEARLY }

/**
 * Hardcoded fallback pricing shown immediately, before (or in place of) a
 * real RevenueCat response. Keeps the paywall's 3-card layout stable
 * offline or before Play Console products exist. Values mirror the
 * approved marketing design; update them here if list pricing changes.
 */
object MockPlans {
    val fallback: List<PlanOption> = listOf(
        PlanOption(plan = PlanType.YEARLY, priceText = "$83.99"),
        PlanOption(plan = PlanType.MONTHLY, priceText = "$11.99", originalPriceText = "$13.99"),
        PlanOption(plan = PlanType.WEEKLY, priceText = "$14.99")
    )
}

data class PaywallUiState(
    val isLoadingOfferings: Boolean = false,
    val plans: List<PlanOption> = MockPlans.fallback,
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

    /**
     * Loads real RevenueCat offerings and, for each plan that has a live
     * match, replaces the mock row with a real one (real formatted price,
     * purchasable package). Plans with no match — offline, no network yet,
     * or a product simply doesn't exist in Play Console/RevenueCat — keep
     * showing their [MockPlans] fallback row, so the three cards never
     * disappear or show an empty/broken state.
     */
    fun loadOfferings() {
        viewModelScope.launch {
            repository.getOfferings()
                .onSuccess { offerings ->
                    val offering: Offering? = offerings.current
                    val plans = _paywallState.value.plans.map { mock ->
                        val packageId = when (mock.plan) {
                            PlanType.WEEKLY -> RevenueCatConfig.PackageIds.WEEKLY
                            PlanType.MONTHLY -> RevenueCatConfig.PackageIds.MONTHLY
                            PlanType.YEARLY -> RevenueCatConfig.PackageIds.YEARLY
                        }
                        val realPackage = offering?.availablePackages?.find { it.identifier == packageId }
                        if (realPackage != null) {
                            PlanOption(
                                plan = mock.plan,
                                priceText = realPackage.product.price.formatted,
                                originalPriceText = null,
                                rcPackage = realPackage
                            )
                        } else {
                            mock
                        }
                    }
                    _paywallState.value = _paywallState.value.copy(
                        isLoadingOfferings = false,
                        plans = plans,
                        errorMessage = null
                    )
                }
                .onFailure {
                    // Offline or RevenueCat unreachable — keep whatever mix of
                    // real/mock rows is already showing rather than surfacing
                    // a scary error over an otherwise-fine-looking paywall.
                    _paywallState.value = _paywallState.value.copy(isLoadingOfferings = false)
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
