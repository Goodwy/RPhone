package dev.goodwy.rphone.controller

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import dev.goodwy.rphone.BuildConfig
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PlayStoreViewModel (
    application: Application,
    private val preferenceManager: PreferenceManager
) : AndroidViewModel(application), PurchaseHelper {
    private lateinit var billingClient: BillingClient

    override fun initBilling() {
        initBillingClient()
    }

    private val _iapSkuDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val iapSkuDetails: StateFlow<List<ProductDetails>> = _iapSkuDetails.asStateFlow()

    private val _subSkuDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subSkuDetails: StateFlow<List<ProductDetails>> = _subSkuDetails.asStateFlow()

    private val _iapSkuDetailsInitialized = MutableStateFlow(false)
    override val iapSkuDetailsInitialized: StateFlow<Boolean> = _iapSkuDetailsInitialized.asStateFlow()

    private val _subSkuDetailsInitialized = MutableStateFlow(false)
    override val subSkuDetailsInitialized: StateFlow<Boolean> = _subSkuDetailsInitialized.asStateFlow()

    private val _iapPurchased = MutableStateFlow<Set<String>>(emptySet())
    val iapPurchased: StateFlow<Set<String>> = _iapPurchased.asStateFlow()

    private val _subPurchased = MutableStateFlow<Set<String>>(emptySet())
    val subPurchased: StateFlow<Set<String>> = _subPurchased.asStateFlow()

    //  Successful purchase (just completed)
    private val _purchaseSuccess = MutableStateFlow(false)
    override val purchaseSuccess: StateFlow<Boolean> = _purchaseSuccess.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBillingReady = MutableStateFlow(false)
    override val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proCheckDone = MutableStateFlow(false)
    override val proCheckDone: StateFlow<Boolean> = _proCheckDone.asStateFlow()

    // We save shopping lists to reload
    private var cachedIaps: List<String> = emptyList()
    private var cachedSubs: List<String> = emptyList()

    // Combined States for the UI
    val isAnyPurchaseReady = combine(
        _iapSkuDetailsInitialized,
        _subSkuDetailsInitialized
    ) { iapReady, subReady ->
        iapReady || subReady
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasAnyPurchases = combine(
        _iapPurchased,
        _subPurchased
    ) { iap, sub ->
        iap.isNotEmpty() || sub.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val consumeResponseListener = ConsumeResponseListener { _, _ -> }

    private fun handlePurchaseIAP(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams
            .newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams,
            acknowledgePurchaseResponseListener
        )
    }

    private val acknowledgePurchaseResponseListener = AcknowledgePurchaseResponseListener { }

    private fun handlePurchaseSub(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams
            .newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(
            acknowledgePurchaseParams,
            acknowledgePurchaseResponseListener
        )
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                viewModelScope.launch {
                    purchase.products.forEach { sku ->
                        // Check whether this is an IAP or a subscription
                        if (_iapPurchased.value.contains(sku) || _subPurchased.value.contains(sku)) {
                            // If this is an IAP
                            if (_iapPurchased.value.contains(sku)) {
                                handlePurchaseIAP(purchase)
                            } else {
                                handlePurchaseSub(purchase)
                            }
                            // Updating shopping lists
                            _purchaseSuccess.value = true
                            refreshPurchases()
                        }
                    }
                }
            }
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach {
                    handlePurchase(it)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // The user canceled the purchase—we don't do anything
            }
            else -> {
                _errorMessage.value = "Purchase failed: ${billingResult.responseCode}"
            }
        }
    }

    private val subsOwnedListener = PurchasesResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            viewModelScope.launch {
                val purchased = purchases.flatMap { it.products }.toSet()
                _subPurchased.value = purchased
                checkAndUpdateProStatus()
            }
        }
    }

    private val subHistoryListener = PurchasesResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.let {
                viewModelScope.launch {
                    val purchased = it.flatMap { purchase -> purchase.products }.toSet()
                    _subPurchased.value = purchased

                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                        subsOwnedListener
                    )
                }
            }
        }
    }

    private val iapHistoryListener = PurchasesResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            viewModelScope.launch {
                val purchased = purchases?.flatMap { it.products }?.toSet() ?: emptySet()
                _iapPurchased.value = purchased
                checkAndUpdateProStatus()
            }
        }
    }

    fun initBillingClient() {
        billingClient = BillingClient.newBuilder(getApplication())
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            ).build()
    }

    fun retrieveDonation(iaps: List<String>, subs: List<String>) {
        cachedIaps = iaps
        cachedSubs = subs

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _purchaseSuccess.value = false // Reset on startup

            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    viewModelScope.launch {
                        _isLoading.value = false
                        _isBillingReady.value = false
                        _errorMessage.value = "Billing service disconnected"
                    }
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    viewModelScope.launch {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            _isBillingReady.value = true
                            loadProductDetails(iaps, subs)
                        } else {
                            _isBillingReady.value = false
                            _errorMessage.value = "Failed to setup billing: ${billingResult.responseCode}"
                            _isLoading.value = false
                        }
                    }
                }
            })
        }
    }

    private suspend fun loadProductDetails(iaps: List<String>, subs: List<String>) {
        try {
            val iapDetails = mutableListOf<ProductDetails>()
            val subDetails = mutableListOf<ProductDetails>()

            // Loading IAP
            if (iaps.isNotEmpty()) {
                val iapProductList = iaps.map { productId ->
                    Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }

                val iapParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(iapProductList)
                    .build()

                billingClient.queryProductDetailsAsync(
                    iapParams
                ) { _, result ->
                    viewModelScope.launch {
                        result.productDetailsList?.let {
                            iapDetails.addAll(it)
                            _iapSkuDetails.value = iapDetails
                            _iapSkuDetailsInitialized.value = true

                            // Loading the IAP purchase history
                            billingClient.queryPurchasesAsync(
                                QueryPurchasesParams.newBuilder()
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .build(),
                                iapHistoryListener
                            )
                        }
                        checkLoadingComplete()
                    }
                }
            } else {
                _iapSkuDetailsInitialized.value = true
            }

            // Loading subscriptions
            if (subs.isNotEmpty()) {
                val subProductList = subs.map { productId ->
                    Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }

                val subParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(subProductList)
                    .build()

                billingClient.queryProductDetailsAsync(
                    subParams
                ) { _, result ->
                    viewModelScope.launch {
                        result.productDetailsList?.let {
                            subDetails.addAll(it)
                            _subSkuDetails.value = subDetails
                            _subSkuDetailsInitialized.value = true

                            // Loading subscription history
                            billingClient.queryPurchasesAsync(
                                QueryPurchasesParams.newBuilder()
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build(),
                                subHistoryListener
                            )
                        }
                        checkLoadingComplete()
                    }
                }
            } else {
                _subSkuDetailsInitialized.value = true
            }

            // If both lists are empty, we immediately stop the download
            if (iaps.isEmpty() && subs.isEmpty()) {
                checkLoadingComplete()
            }

        } catch (e: Exception) {
            _errorMessage.value = e.message
            _isLoading.value = false
        }
    }

    private suspend fun checkLoadingComplete() {
        if (_iapSkuDetailsInitialized.value && _subSkuDetailsInitialized.value) {
            _isLoading.value = false
        }
    }

    private suspend fun refreshPurchases() {
        // Updating IAP Purchases
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            iapHistoryListener
        )

        // Updating Subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            subHistoryListener
        )
    }

    override fun refreshAllData() {
        viewModelScope.launch {
            // Reset the download status
            _isLoading.value = true
            _iapSkuDetailsInitialized.value = false
            _subSkuDetailsInitialized.value = false

            // Refreshing prices
            if (cachedIaps.isNotEmpty() || cachedSubs.isNotEmpty()) {
                loadProductDetails(cachedIaps, cachedSubs)
            } else {
                _isLoading.value = false
                _errorMessage.value = "No products to load"
            }

            // Updating purchases
            refreshPurchases()
        }
    }


    override fun purchaseDonation(product: String, activity: Activity) {
        viewModelScope.launch {
            try {
                val iapSku = _iapSkuDetails.value.firstOrNull { it.productId == product }
                if (iapSku != null) {
                    val productDetailsParamsList = ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(iapSku)
                        .build()

                    val flowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(productDetailsParamsList))
                        .build()

                    billingClient.launchBillingFlow(activity, flowParams)
                } else {
                    _errorMessage.value = "Product not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    override fun purchaseSubscription(product: String, activity: Activity, planId: String?) {
        viewModelScope.launch {
            try {
                val subSku = _subSkuDetails.value.firstOrNull { it.productId == product }
                if (subSku != null) {
                    val plan = if (planId != null) {
                        subSku.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }
                    } else {
                        subSku.subscriptionOfferDetails?.firstOrNull()
                    }

                    if (plan != null) {
                        val productDetailsParamsList = ProductDetailsParams.newBuilder()
                            .setOfferToken(plan.offerToken)
                            .setProductDetails(subSku)
                            .build()

                        val flowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(listOf(productDetailsParamsList))
                            .build()

                        billingClient.launchBillingFlow(activity, flowParams)
                    } else {
                        _errorMessage.value = "Plan not found"
                    }
                } else {
                    _errorMessage.value = "Subscription not found"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    override fun getPriceDonation(product: String): String {
        return _iapSkuDetails.value.firstOrNull { it.productId == product }
            ?.oneTimePurchaseOfferDetails?.formattedPrice
            ?: getApplication<Application>().getString(R.string.no_connection)
    }

    override fun getPriceSubscription(product: String, planId: String?): String {
        val subSku = _subSkuDetails.value.firstOrNull { it.productId == product }
            ?: return getApplication<Application>().getString(R.string.no_connection)

        return try {
            val plan = if (planId != null) {
                subSku.subscriptionOfferDetails?.firstOrNull { it.basePlanId == planId }
            } else {
                subSku.subscriptionOfferDetails?.firstOrNull()
            }
            plan?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: getApplication<Application>().getString(R.string.no_connection)
        } catch (e: Exception) {
            getApplication<Application>().getString(R.string.no_connection)
        }
    }

    override fun isIapPurchased(product: String): Boolean {
        return _iapPurchased.value.contains(product)
    }

    override fun isSubPurchased(product: String): Boolean {
        return _subPurchased.value.contains(product)
    }

    override fun restorePurchases() {
        viewModelScope.launch {
            _isLoading.value = true
            refreshPurchases()
            _isLoading.value = false
        }
    }

    override fun clearErrors() {
        _errorMessage.value = null
    }

    override fun clearPurchaseSuccess() {
        _purchaseSuccess.value = false
    }

    override fun onCleared() {
        super.onCleared()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }

    private suspend fun checkAndUpdateProStatus() {
        // Checking IAP Purchases
        val iapProPurchased = _iapPurchased.value.any {
            it == BuildConfig.PRODUCT_ID_X1
        }

        // Checking Subscriptions
        val subProPurchased = _subPurchased.value.any {
            it == BuildConfig.SUBSCRIPTION_ID_X1 ||
                    it == BuildConfig.SUBSCRIPTION_YEAR_ID_X1
        }

        // Updating the status
        _isPro.value = iapProPurchased || subProPurchased
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_IAP, iapProPurchased)
        preferenceManager.setBoolean(PreferenceManager.KEY_IS_PRO_SUB, subProPurchased)
        _proCheckDone.value = true
    }

    // Full Pro Status Check with Purchase History Download
    override fun checkProStatus() {
        viewModelScope.launch {
            // If billing has not been initialized, let's initialize it
            if (!::billingClient.isInitialized) {
                initBillingClient()
            }

            // If billing isn't ready, we'll set it up
            if (!_isBillingReady.value) {
                _isLoading.value = true
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        viewModelScope.launch {
                            _isLoading.value = false
                            _errorMessage.value = "Billing service disconnected"
                        }
                    }

                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        viewModelScope.launch {
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                _isBillingReady.value = true
                                // Loading purchases
                                loadPurchasesOnly()
                            } else {
                                _isLoading.value = false
                                _errorMessage.value = "Failed to setup billing"
                            }
                        }
                    }
                })
            } else {
                // If the billing is already ready, just upload the purchases
                loadPurchasesOnly()
            }
        }
    }

    // We import only purchases, without product details (prices)
    private suspend fun loadPurchasesOnly() {
        _isLoading.value = true

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            iapHistoryListener
        )

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            subHistoryListener
        )

        // We are waiting for the audit to be completed
        delay(1000) // A slight delay to ensure the warranty
        _isLoading.value = false
    }

    // A method for obtaining Pro status simultaneously
    fun isProVersion(): Boolean {
        return _isPro.value
    }

    // A method for obtaining Pro status with verification
    suspend fun isProVersionWithCheck(): Boolean {
        if (!_proCheckDone.value) {
            checkProStatus()
            delay(500)
        }
        return _isPro.value
    }

    // Method for Setting Status Immediately (Without Loading)
    override fun setProStatusImmediate(isPro: Boolean) {
        _isPro.value = isPro
        _proCheckDone.value = true
    }
}

sealed class Tipping {
    data object FailedToLoad : Tipping()
    data object Succeeded : Tipping()
    data object NoTips : Tipping()
}