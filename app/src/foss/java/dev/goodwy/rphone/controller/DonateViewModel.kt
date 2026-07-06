package dev.goodwy.rphone.controller

import android.app.Activity
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DonateViewModel (
    private val preferenceManager: PreferenceManager
) : PurchaseHelper {

    private val _isPro = MutableStateFlow(
        preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
    )
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proCheckDone = MutableStateFlow(true)
    override val proCheckDone: StateFlow<Boolean> = _proCheckDone.asStateFlow()

    private val _purchaseSuccess = MutableStateFlow(false)
    override val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBillingReady = MutableStateFlow(false)
    override val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _iapSkuDetailsInitialized = MutableStateFlow(false)
    override val iapSkuDetailsInitialized: StateFlow<Boolean> = _iapSkuDetailsInitialized.asStateFlow()

    private val _subSkuDetailsInitialized = MutableStateFlow(false)
    override val subSkuDetailsInitialized: StateFlow<Boolean> = _subSkuDetailsInitialized.asStateFlow()

    override fun initBilling() {
        // Stub - do nothing
        _isBillingReady.value = false
    }

    override fun checkProStatus() {
        val isPro = preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_FOSS, false)
        _isPro.value = isPro
        _proCheckDone.value = true
    }

    override fun setProStatusImmediate(isPro: Boolean) {
        _isPro.value = isPro
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_FOSS, isPro)
        _proCheckDone.value = true
    }

    override fun clearErrors() {
        _errorMessage.value = null
    }

    override fun clearPurchaseSuccess() {
        _purchaseSuccess.value = false
    }

    override fun getPriceDonation(product: String): String {
        return "Free"
    }

    override fun getPriceSubscription(product: String, planId: String?): String {
        return "Free"
    }

    override fun isIapPurchased(product: String): Boolean {
        return false
    }

    override fun isSubPurchased(product: String): Boolean {
        return false
    }

    override fun purchaseDonation(product: String, activity: Activity) {
        // Stub
    }

    override fun purchaseSubscription(product: String, activity: Activity, planId: String?) {
        // Stub
    }

    override fun restorePurchases() {
        // Stub
    }

    override fun refreshAllData() {
        // Stub
    }
}