package dev.fanfly.wingslog.feature.subscription.viewing.paywall

import androidx.compose.runtime.Composable

/**
 * The RevenueCat-hosted paywall, where the platform has one.
 *
 * `expect`/`actual` because the paywall UI artifact (`purchases-kmp-ui`) is Android/iOS only, and
 * this module also compiles for the web app. The web actual renders nothing — the screen never asks
 * for a paywall there, because `BillingManager.isPurchaseSupported` is false and the UI offers a
 * "subscribe on iOS or Android" message instead.
 *
 * @param onPurchaseCompleted the store accepted a purchase. Entitlement is not yet in force: the
 *   caller shows an "activating" state until the webhook-written entitlement syncs down.
 */
@Composable
expect fun ProPaywallHost(
  onPurchaseCompleted: () -> Unit,
  onDismiss: () -> Unit,
)

/**
 * RevenueCat's Customer Center — cancel, change plan, restore, request a refund.
 *
 * Same platform story as [ProPaywallHost]. Worth deferring to rather than hand-rolling: these flows
 * are store policy (and Apple/Google keep changing them), not product decisions of ours.
 */
@Composable
expect fun CustomerCenterHost(onDismiss: () -> Unit)
