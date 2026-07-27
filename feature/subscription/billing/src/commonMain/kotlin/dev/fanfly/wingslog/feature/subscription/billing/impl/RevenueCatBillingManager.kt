package dev.fanfly.wingslog.feature.subscription.billing.impl

import co.touchlab.kermit.Logger
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitLogOut
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import dev.fanfly.wingslog.feature.subscription.billing.RevenueCatApiKey
import dev.fanfly.wingslog.feature.subscription.billing.platformBillingStore
import dev.fanfly.wingslog.feature.subscription.model.BillingError
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.BillingPeriod
import dev.fanfly.wingslog.feature.subscription.model.PRO_ENTITLEMENT_ID
import dev.fanfly.wingslog.feature.subscription.model.PRO_OFFERING_ID
import dev.fanfly.wingslog.feature.subscription.model.ProOffering
import dev.fanfly.wingslog.feature.subscription.model.ProPackage
import dev.fanfly.wingslog.feature.subscription.model.PurchaseOutcome
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import dev.fanfly.wingslog.feature.subscription.model.StoreCustomerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [BillingManager] backed by the RevenueCat KMP SDK. Android and iOS only — see the module's
 * `build.gradle.kts` for why this cannot live in an all-targets module.
 *
 * Reminder on the division of responsibility (subscription_design.html §1): this class starts
 * purchases, it does **not** grant entitlement. Once the store accepts a purchase, RevenueCat
 * validates the receipt server-side and posts a webhook that the `revenueCatWebhook` function
 * normalizes into `subscriptions/{uid}`. Only that synced doc flips the app to Pro, on every
 * platform including web. Nothing here writes entitlement, and [customerInfo] is used to *observe*
 * the store, never to unlock a feature.
 *
 * @param isDeveloperBuild selects the Test Store key over the production key; see [RevenueCatApiKey].
 * @param verboseLogging RevenueCat's own SDK logging. On in developer builds only.
 */
class RevenueCatBillingManager(
  isDeveloperBuild: Boolean,
  verboseLogging: Boolean = isDeveloperBuild,
) : BillingManager {

  private val apiKey: String? = RevenueCatApiKey.resolve(isDeveloperBuild)

  private val customerInfoState = MutableStateFlow<StoreCustomerInfo>(StoreCustomerInfo.Unknown)

  /**
   * False when no key is configured for this build — a release build before the store products go
   * live. Callers render "not available on this build" rather than a dead buy button, and every
   * other method below short-circuits.
   */
  override val isPurchaseSupported: Boolean = apiKey != null

  /**
   * `null` when unconfigured: with no SDK there is no Customer Center to open, so the UI must treat
   * this build as unable to manage anything rather than as a Play/App Store client.
   */
  override val store: PurchasePlatform? =
    if (isPurchaseSupported) platformBillingStore else null

  init {
    if (apiKey == null) {
      logger.i {
        "No RevenueCat API key for this build — purchasing disabled. Expected while the store " +
          "products are not live; the app still reads entitlement from the synced subscription doc."
      }
    } else {
      configure(apiKey, verboseLogging)
    }
  }

  private fun configure(key: String, verboseLogging: Boolean) {
    // `configure` sets a process-wide singleton. Guarded because a second call would reset SDK
    // state, and both hosts can rebuild their Koin graph (iOS re-entering `doInitKoin`).
    if (Purchases.isConfigured) {
      logger.d { "RevenueCat already configured; reusing the shared instance." }
    } else {
      if (verboseLogging) Purchases.logLevel = LogLevel.DEBUG
      // No appUserId here: the user may not be signed in yet. The identity coordinator calls
      // `setAppUserId` on every auth change, which aliases this anonymous id onto the real uid.
      Purchases.configure(PurchasesConfiguration.Builder(apiKey = key).build())
    }
    // One long-lived delegate feeding the shared flow — rather than a `callbackFlow` per collector,
    // which would fight over the SDK's single delegate slot and drop updates for all but the last.
    Purchases.sharedInstance.delegate = CustomerInfoDelegate(customerInfoState)
  }

  override suspend fun proOffering(): ProOffering {
    if (apiKey == null) return ProOffering.Unavailable(BillingError.UNSUPPORTED)
    return try {
      val offerings = Purchases.sharedInstance.awaitOfferings()
      // Fall back to `current` so a dashboard that promotes a differently-named offering still works.
      val offering = offerings.getOffering(PRO_OFFERING_ID) ?: offerings.current
      val packages = offering?.availablePackages.orEmpty().map { it.toProPackage() }
      if (packages.isEmpty()) {
        logger.w { "RevenueCat returned no packages for offering '$PRO_OFFERING_ID'." }
        ProOffering.Unavailable(BillingError.UNKNOWN)
      } else {
        ProOffering.Available(packages)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: PurchasesException) {
      logger.w(e) { "Failed to load RevenueCat offerings." }
      ProOffering.Unavailable(e.error.toBillingError())
    }
  }

  override suspend fun purchase(pkg: ProPackage): PurchaseOutcome {
    if (apiKey == null) return PurchaseOutcome.Failed(BillingError.UNSUPPORTED)
    val storePackage = findPackage(pkg.id)
      ?: return PurchaseOutcome.Failed(BillingError.UNKNOWN)
    return try {
      Purchases.sharedInstance.awaitPurchase(packageToPurchase = storePackage)
      // Purchased at the *store*. Entitlement follows via webhook → applyEntitlement → sync; the
      // caller shows "activating…" until the synced doc says Pro.
      logger.i { "Store purchase completed; awaiting entitlement webhook." }
      PurchaseOutcome.Purchased
    } catch (e: CancellationException) {
      throw e
    } catch (e: PurchasesTransactionException) {
      if (e.userCancelled) {
        PurchaseOutcome.Cancelled
      } else {
        logger.w(e) { "Purchase failed." }
        PurchaseOutcome.Failed(e.error.toBillingError())
      }
    }
  }

  override suspend fun restorePurchases(): PurchaseOutcome {
    if (apiKey == null) return PurchaseOutcome.Failed(BillingError.UNSUPPORTED)
    return try {
      val info = Purchases.sharedInstance.awaitRestore()
      customerInfoState.value = info.toStoreCustomerInfo()
      if (info.entitlements.active.containsKey(PRO_ENTITLEMENT_ID)) {
        PurchaseOutcome.Purchased
      } else {
        PurchaseOutcome.Failed(BillingError.NOTHING_TO_RESTORE)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: PurchasesException) {
      logger.w(e) { "Restore failed." }
      PurchaseOutcome.Failed(e.error.toBillingError())
    }
  }

  override fun customerInfo(): Flow<StoreCustomerInfo> = customerInfoState.asStateFlow()

  override suspend fun setAppUserId(uid: String?) {
    if (apiKey == null) return
    try {
      val info = if (uid == null) {
        // Back to an anonymous store identity so the next account can't inherit this one's receipts.
        Purchases.sharedInstance.awaitLogOut()
      } else if (Purchases.sharedInstance.appUserID == uid) {
        // Already aliased — re-logging in on every auth emission would be a redundant network call.
        return
      } else {
        Purchases.sharedInstance.awaitLogIn(uid).customerInfo
      }
      customerInfoState.value = info.toStoreCustomerInfo()
    } catch (e: CancellationException) {
      throw e
    } catch (e: PurchasesException) {
      // Non-fatal: purchasing may fail until this succeeds, but entitlement still syncs normally.
      logger.w(e) { "Failed to bind the store identity to the signed-in account." }
    }
  }

  /** Refreshes [customerInfo] from the store; safe to call after returning to the app. */
  suspend fun refreshCustomerInfo() {
    if (apiKey == null) return
    try {
      customerInfoState.value = Purchases.sharedInstance.awaitCustomerInfo().toStoreCustomerInfo()
    } catch (e: CancellationException) {
      throw e
    } catch (e: PurchasesException) {
      logger.w(e) { "Failed to refresh customer info." }
    }
  }

  private suspend fun findPackage(packageId: String): Package? = try {
    val offerings = Purchases.sharedInstance.awaitOfferings()
    val offering = offerings.getOffering(PRO_OFFERING_ID) ?: offerings.current
    offering?.availablePackages?.firstOrNull { it.identifier == packageId }
      .also { if (it == null) logger.w { "No RevenueCat package '$packageId' in the offering." } }
  } catch (e: CancellationException) {
    throw e
  } catch (e: PurchasesException) {
    logger.w(e) { "Failed to resolve package '$packageId'." }
    null
  }

  private companion object {
    private val logger = Logger.withTag("RevenueCatBillingManager")
  }
}

/** Feeds SDK-pushed customer updates (renewals, expirations, promo redemptions) into the flow. */
private class CustomerInfoDelegate(
  private val state: MutableStateFlow<StoreCustomerInfo>,
) : PurchasesDelegate {

  override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
    state.value = customerInfo.toStoreCustomerInfo()
  }

  override fun onPurchasePromoProduct(
    product: StoreProduct,
    startPurchase: (
      onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
      onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit,
    ) -> Unit,
  ) {
    // App Store promoted in-app purchases. Deferring is allowed and is the safe default: we have no
    // UI to interrupt, and the pilot can still buy from the paywall.
  }
}

private fun CustomerInfo.toStoreCustomerInfo() = StoreCustomerInfo.Known(
  hasActiveProEntitlement = entitlements.active.containsKey(PRO_ENTITLEMENT_ID),
  managementUrl = managementUrlString,
)

private fun Package.toProPackage() = ProPackage(
  id = identifier,
  period = packageType.toBillingPeriod(),
  formattedPrice = storeProduct.price.formatted,
  formattedPricePerMonth = storeProduct.pricePerMonth?.formatted,
)

private fun PackageType.toBillingPeriod(): BillingPeriod = when (this) {
  PackageType.MONTHLY -> BillingPeriod.MONTHLY
  PackageType.ANNUAL -> BillingPeriod.YEARLY
  else -> BillingPeriod.OTHER
}

/**
 * Collapses RevenueCat's ~40 error codes onto the handful of outcomes the UI actually varies on.
 * Anything unmapped is [BillingError.UNKNOWN] — a generic "couldn't complete that" beats inventing
 * advice we can't stand behind.
 */
private fun PurchasesError.toBillingError(): BillingError = when (code) {
  PurchasesErrorCode.NetworkError,
  PurchasesErrorCode.OfflineConnectionError,
  PurchasesErrorCode.ApiEndpointBlocked,
  -> BillingError.NETWORK

  PurchasesErrorCode.StoreProblemError,
  PurchasesErrorCode.PurchaseNotAllowedError,
  PurchasesErrorCode.PurchaseInvalidError,
  PurchasesErrorCode.ProductNotAvailableForPurchaseError,
  PurchasesErrorCode.PaymentPendingError,
  -> BillingError.STORE_PROBLEM

  PurchasesErrorCode.ReceiptAlreadyInUseError,
  PurchasesErrorCode.MissingReceiptFileError,
  -> BillingError.NOTHING_TO_RESTORE

  PurchasesErrorCode.UnsupportedError,
  PurchasesErrorCode.ConfigurationError,
  -> BillingError.UNSUPPORTED

  else -> BillingError.UNKNOWN
}
