package dev.goodwy.rphone.controller

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

interface PurchaseHelper {
    val isPro: StateFlow<Boolean>
    val proCheckDone: StateFlow<Boolean>
    val purchaseSuccess: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>
    val isBillingReady: StateFlow<Boolean>
    val iapSkuDetailsInitialized: StateFlow<Boolean>
    val subSkuDetailsInitialized: StateFlow<Boolean>

    fun initBilling()
    fun checkProStatus()
    fun setProStatusImmediate(isPro: Boolean)
    fun clearErrors()
    fun clearPurchaseSuccess()

    fun getPriceDonation(product: String): String
    fun getPriceSubscription(product: String, planId: String? = null): String
    fun isIapPurchased(product: String): Boolean
    fun isSubPurchased(product: String): Boolean

    fun purchaseDonation(product: String, activity: Activity)
    fun purchaseSubscription(product: String, activity: Activity, planId: String? = null)
    fun restorePurchases()
    fun refreshAllData()
    fun clearProPrefsIfNeeded()

    // Empty default implementation for Google Play
    // RuStoreViewModel will override this method
    fun loadProducts(productIds: List<String>) {}
    fun handleNewIntent(intent: Intent?, activity: ComponentActivity) {}
}