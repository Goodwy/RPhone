package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.*

class RuStoreViewModel(
    application: Application,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application), PurchaseHelper {

    private val intentInteractor by lazy { RuStorePayClient.instance.getIntentInteractor() }
    private val productInteractor by lazy { RuStorePayClient.instance.getProductInteractor() }
    private val purchaseInteractor by lazy { RuStorePayClient.instance.getPurchaseInteractor() }
    private val userInteractor by lazy { RuStorePayClient.instance.getUserInteractor() }

    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proCheckDone = MutableStateFlow(false)
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

    private var cachedProductIds: List<String> = emptyList()

    private val productsList = mutableListOf<ProductInfo>()
    private val purchaseCache = mutableListOf<PurchaseInfo>()

    private val _iapPurchased = MutableStateFlow<Set<String>>(emptySet())
    private val _subPurchased = MutableStateFlow<Set<String>>(emptySet())

    private var currentActivity: ComponentActivity? = null

    fun setCurrentActivity(activity: ComponentActivity?) {
        currentActivity = activity
        activity?.let {
            intentInteractor.proceedIntent(
                intent = it.intent,
                sdkTheme = if (isDarkMode(it)) SdkTheme.DARK else SdkTheme.LIGHT
            )
            if (!_isBillingReady.value) {
                checkPaymentAvailability()
            }
        }
    }

    override fun handleNewIntent(intent: Intent?, activity: ComponentActivity) {
        intentInteractor.proceedIntent(
            intent,
            sdkTheme = if (isDarkMode(activity)) SdkTheme.DARK else SdkTheme.LIGHT
        )
    }

    private fun isDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun checkPaymentAvailability() {
        _isLoading.value = true
        purchaseInteractor
            .getPurchaseAvailability()
            .addOnSuccessListener { result ->
                when (result) {
                    is PurchaseAvailabilityResult.Available -> {
                        _isBillingReady.value = true
                        loadProducts(
                            listOf(
                                BuildConfig.PRODUCT_ID_X1,
                                BuildConfig.SUBSCRIPTION_ID_X1,
                                BuildConfig.SUBSCRIPTION_YEAR_ID_X1,
                            )
                        )
                    }
                    is PurchaseAvailabilityResult.Unavailable -> {
                        _isBillingReady.value = false
                        _isLoading.value = false
                        _errorMessage.value = "Payments unavailable: ${result.cause}"
                    }
                }
            }
            .addOnFailureListener { error ->
                _isBillingReady.value = false
                _isLoading.value = false
                _errorMessage.value = "Failed to check availability: ${error.message}"
            }
    }

    override fun loadProducts(productIds: List<String>) {
        cachedProductIds = productIds
        _isLoading.value = true

        productInteractor
            .getProducts(productsId = productIds.map { ProductId(it) })
            .addOnSuccessListener { products ->
                productsList.clear()
                var hasIap = false
                var hasSub = false

                products.forEach { product ->
                    val productInfo = ProductInfo(
                        productId = product.productId.value,
                        type = product.type.name,
                        amountLabel = product.amountLabel.value,
                        price = product.price?.value ?: 0,
                        currency = product.currency.value,
                        imageUrl = product.imageUrl.value,
                        title = product.title.value,
                        description = product.description?.value,
                        subscriptionInfo = product.subscriptionInfo?.let { subInfo ->
                            SubscriptionInfo(
                                periods = subInfo.periods.map { period ->
                                    when (period) {
                                        is TrialPeriod -> SubscriptionPeriod("trial", period.duration, period.price.toString())
                                        is PromoPeriod -> SubscriptionPeriod("promo", period.duration, period.price.toString())
                                        is MainPeriod -> SubscriptionPeriod("main", period.duration, period.price.toString())
                                        is GracePeriod -> SubscriptionPeriod("grace", period.duration, null)
                                        is HoldPeriod -> SubscriptionPeriod("hold", period.duration, null)
                                    }
                                }
                            )
                        }
                    )
                    productsList.add(productInfo)

                    if (product.type == ProductType.CONSUMABLE_PRODUCT || product.type == ProductType.NON_CONSUMABLE_PRODUCT) hasIap = true
                    if (product.type == ProductType.SUBSCRIPTION) hasSub = true
                }

                _iapSkuDetailsInitialized.value = hasIap
                _subSkuDetailsInitialized.value = hasSub

                // DO NOT reset _isLoading here!
                // It will be reset in checkPurchases() after the actual purchase list is received
                checkPurchases()
            }
            .addOnFailureListener { error ->
                _errorMessage.value = "Failed to load products: ${error.message}"
                _isLoading.value = false
            }
    }

    private fun checkPurchases() {
        purchaseInteractor
            .getPurchases()
            .addOnSuccessListener { purchases ->
                purchaseCache.clear()
                val iapSet = mutableSetOf<String>()
                val subSet = mutableSetOf<String>()

                purchaseCache.addAll(
                    purchases.mapNotNull { purchase ->
                        when (purchase) {
                            is ProductPurchase -> {
                                iapSet.add(purchase.productId.value)
                                PurchaseInfo(
                                    purchaseId = purchase.purchaseId.value,
                                    invoiceId = purchase.invoiceId.value,
                                    type = "product",
                                    status = purchase.status.name,
                                    purchaseTime = purchase.purchaseTime?.time ?: 0,
                                    price = purchase.price.value,
                                    currency = purchase.currency.value,
                                    developerPayload = purchase.developerPayload?.value,
                                    productId = purchase.productId.value,
                                )
                            }
                            is SubscriptionPurchase -> {
                                subSet.add(purchase.productId.value)
                                PurchaseInfo(
                                    purchaseId = purchase.purchaseId.value,
                                    invoiceId = purchase.invoiceId.value,
                                    type = "subscription",
                                    status = purchase.status.name,
                                    purchaseTime = purchase.purchaseTime?.time ?: 0,
                                    price = purchase.price.value,
                                    currency = purchase.currency.value,
                                    productId = purchase.productId.value,
                                    expirationDate = purchase.expirationDate.time,
                                    gracePeriodEnabled = purchase.gracePeriodEnabled,
                                )
                            }
                            else -> null
                        }
                    }
                )

                _iapPurchased.value = iapSet
                _subPurchased.value = subSet

                updateProStatus()

                _isLoading.value = false
            }
            .addOnFailureListener { error ->
                _errorMessage.value = "Failed to fetch purchases: ${error.message}"
                _isLoading.value = false
            }
    }

    private fun updateProStatus() {
        val iapProPurchased = _iapPurchased.value.any { it == BuildConfig.PRODUCT_ID_X1 }
        val subProPurchased = _subPurchased.value.any {
            it == BuildConfig.SUBSCRIPTION_ID_X1 || it == BuildConfig.SUBSCRIPTION_YEAR_ID_X1
        }

        val isPro = iapProPurchased || subProPurchased

        // If the status has changed from Pro to standard (meaning a refund or the end of the subscription)
        if (_isPro.value && !isPro) {
            clearProPrefsIfNeeded()
        }

        _isPro.value = isPro
        _proCheckDone.value = true

        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, iapProPurchased)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, subProPurchased)
    }

    override fun clearProPrefsIfNeeded() {
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)

        val themeMode = preferenceManager.getString(PreferenceManager.KEY_THEME_MODE, "auto") ?: "auto"
        if (themeMode == "auto_bw" || themeMode == "white" || themeMode == "black" ) {
            preferenceManager.setString(PreferenceManager.KEY_THEME_MODE, "auto")
        }
        preferenceManager.setBoolean(PreferenceManager.KEY_DYNAMIC_COLORS, true)
    }

    override fun initBilling() {}

    override fun checkProStatus() {
        val savedIsProIap = preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_IAP, false)
        val savedIsProSub = preferenceManager.getBoolean(PreferenceManager.KEY_IS_PRO_SUB, false)

        if (savedIsProIap || savedIsProSub) {
            _isPro.value = true
            _proCheckDone.value = true
        } else {
            // If there are no saved purchases, we STILL set proCheckDone = true,
            // so that the settings screen can immediately display the in-app purchase banner.
            // If RuStore detects a purchase later, it will update _isPro to true.
            _proCheckDone.value = true
        }

        if (_isBillingReady.value) {
            checkPurchases()
        }
    }

    override fun setProStatusImmediate(isPro: Boolean) {
        _isPro.value = isPro
        _proCheckDone.value = true
    }

    override fun clearErrors() { _errorMessage.value = null }
    override fun clearPurchaseSuccess() { _purchaseSuccess.value = false }

    override fun getPriceDonation(product: String): String {
        return productsList.firstOrNull { it.productId == product }?.amountLabel ?: "..."
    }

    override fun getPriceSubscription(product: String, planId: String?): String {
        return productsList.firstOrNull { it.productId == product }?.amountLabel ?: "..."
    }

    override fun isIapPurchased(product: String): Boolean {
        return _iapPurchased.value.contains(product)
    }

    override fun isSubPurchased(product: String): Boolean {
        return _subPurchased.value.contains(product)
    }

    override fun purchaseDonation(product: String, activity: Activity) {
        if (activity !is ComponentActivity) {
            _errorMessage.value = "Activity must be ComponentActivity"
            return
        }

        _isLoading.value = true

        userInteractor
            .getUserAuthorizationStatus()
            .addOnSuccessListener { status ->
                when (status) {
                    UserAuthorizationStatus.AUTHORIZED -> {
                        // If you are logged in, proceed with the purchase
                        executePurchase(product, activity)
                    }
                    UserAuthorizationStatus.UNAUTHORIZED -> {
                        // If you are not logged in, open the RuStore login screen
                        _isLoading.value = false
                        ru.rustore.sdk.core.util.RuStoreUtils.openRuStoreAuthorization(activity)
                    }
                }
            }
            .addOnFailureListener { error ->
                _isLoading.value = false
                _errorMessage.value = "Auth check failed: ${error.message}"
            }
    }

    private fun executePurchase(product: String, activity: ComponentActivity) {
        val purchaseActivity = currentActivity ?: activity
        val theme = if (isDarkMode(purchaseActivity)) SdkTheme.DARK else SdkTheme.LIGHT
        val params = ProductPurchaseParams(productId = ProductId(product))

        purchaseInteractor
            .purchase(params, PreferredPurchaseType.ONE_STEP, theme)
            .addOnSuccessListener {
                _purchaseSuccess.value = true
                // Don't reset `isLoading` here; `checkPurchases` will do that on its own.
                checkPurchases()
            }
            .addOnFailureListener { error ->
                _isLoading.value = false
                when (error) {
                    is RuStorePaymentException.ProductPurchaseCancelled -> { }
                    is RuStorePaymentException.ProductPurchaseException -> _errorMessage.value = "Purchase error: ${error.message}"
                    is RuStorePaymentException.RuStorePaymentNetworkException -> _errorMessage.value = "Network error: ${error.message}"
                    else -> _errorMessage.value = "Error: ${error.message}"
                }
            }
    }

    override fun purchaseSubscription(product: String, activity: Activity, planId: String?) {
        purchaseDonation(product, activity)
    }

    override fun restorePurchases() {
        _isLoading.value = true
        _errorMessage.value = null

        if (cachedProductIds.isNotEmpty()) {
            loadProducts(cachedProductIds)
        } else {
            checkPurchases()
        }
    }

    override fun refreshAllData() {
        _isLoading.value = true
        _iapSkuDetailsInitialized.value = false
        _subSkuDetailsInitialized.value = false
        productsList.clear()
        purchaseCache.clear()
        _iapPurchased.value = emptySet()
        _subPurchased.value = emptySet()
        loadProducts(cachedProductIds)
    }
}

data class ProductInfo(
    val productId: String,
    val type: String,
    val amountLabel: String,
    val price: Int,
    val currency: String,
    val imageUrl: String?,
    val title: String,
    val description: String?,
    val subscriptionInfo: SubscriptionInfo? = null
)

data class SubscriptionInfo(
    val periods: List<SubscriptionPeriod>
)

data class SubscriptionPeriod(
    val type: String,
    val duration: String,
    val price: String?
)

data class PurchaseInfo(
    val purchaseId: String,
    val invoiceId: String,
    val type: String,
    val status: String,
    val purchaseTime: Long,
    val price: Int,
    val currency: String,
    val developerPayload: String? = null,
    val productId: String,
    val expirationDate: Long? = null,
    val gracePeriodEnabled: Boolean? = null
)