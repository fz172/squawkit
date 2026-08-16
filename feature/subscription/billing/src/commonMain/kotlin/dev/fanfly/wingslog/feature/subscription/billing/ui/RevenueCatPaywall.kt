package dev.fanfly.wingslog.feature.subscription.billing.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.StoreTransaction
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions
import dev.fanfly.wingslog.feature.subscription.model.PRO_ENTITLEMENT_ID

/**
 * The RevenueCat-hosted paywall, laid out by the dashboard's Paywall Editor rather than by us.
 *
 * Server-driven on purpose: pricing, benefit copy and layout are merchandising, and iterating on them
 * shouldn't require a store release. The trade-off is that this surface does not follow
 * `WingslogTheme` — it renders the dashboard's own design. The in-app [ProUpsellSheet]
 * (`subscription/viewing`) stays the themed, contextual promo; this is the full purchase screen it
 * routes to.
 *
 * @param onPurchaseCompleted the store accepted a purchase. Entitlement is not yet in force — the
 *   caller should show "activating" until the webhook-written entitlement syncs, never flip to Pro
 *   locally (subscription_design.html §1).
 * @param onDismiss the paywall closed, whether by the close button or by a completed purchase.
 */
@Composable
fun RevenueCatProPaywall(
  onPurchaseCompleted: () -> Unit,
  onDismiss: () -> Unit,
) {
  // The SDK holds the listener across recompositions, so read callbacks through a snapshot state
  // rather than capturing the first composition's lambdas.
  val currentOnPurchaseCompleted by rememberUpdatedState(onPurchaseCompleted)
  val currentOnDismiss by rememberUpdatedState(onDismiss)

  val options = PaywallOptions.Builder(dismissRequest = { currentOnDismiss() })
    .apply {
      // Offering left null: the SDK serves whichever offering the dashboard marks current, so the
      // promoted plan can change without an app release.
      shouldDisplayDismissButton = true
      listener = object : PaywallListener {
        override fun onPurchaseCompleted(
          customerInfo: CustomerInfo,
          storeTransaction: StoreTransaction,
        ) {
          logger.i { "Paywall purchase completed; awaiting entitlement webhook." }
          currentOnPurchaseCompleted()
        }

        override fun onRestoreCompleted(customerInfo: CustomerInfo) {
          // "Completed" only means the restore ran — it fires even when the store had nothing to
          // give back. Signalling completion regardless would leave the page stuck on "activating"
          // forever, waiting for an entitlement no purchase will ever produce. Only a restore that
          // actually returned the Pro entitlement is worth waiting on.
          val restoredPro = customerInfo.entitlements.active.containsKey(PRO_ENTITLEMENT_ID)
          logger.i { "Paywall restore completed; restored Pro: $restoredPro" }
          if (restoredPro) currentOnPurchaseCompleted()
        }

        override fun onPurchaseCancelled() {
          logger.d { "Paywall purchase cancelled by the pilot." }
        }

        override fun onPurchaseError(error: PurchasesError) {
          logger.w { "Paywall purchase error: $error" }
        }

        override fun onRestoreError(error: PurchasesError) {
          logger.w { "Paywall restore error: $error" }
        }

        override fun onPurchaseStarted(rcPackage: Package) {
          logger.d { "Paywall purchase started: ${rcPackage.identifier}" }
        }
      }
    }
    .build()

  Paywall(options = options)
}

/**
 * RevenueCat's Customer Center — the self-service surface for an existing subscriber: cancel, change
 * plan, restore, request a refund, and the store-specific flows behind each.
 *
 * Worth using rather than hand-rolling because these paths are mostly store policy, not product
 * decisions, and Apple/Google keep changing them. Shown from the Subscription page when the account
 * is already Pro; a free account sees the paywall instead.
 *
 * `fillMaxSize()` isn't cosmetic here: on iOS, `CustomerCenter()` is backed by a
 * `UIKitViewController` wrapping a native `RCCustomerCenterViewController`. With no explicit size
 * the interop view gets no layout height and renders blank — [Paywall] doesn't need this because
 * it sizes itself differently under the hood. See
 * github.com/RevenueCat/purchases-kmp/issues/682.
 */
@Composable
fun RevenueCatCustomerCenter(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier.fillMaxSize(),
) {
  CustomerCenter(modifier = modifier, onDismiss = onDismiss)
}

private val logger = Logger.withTag("RevenueCatPaywall")
