package dev.fanfly.wingslog.feature.subscription.viewing.paywall

import androidx.compose.runtime.Composable

/**
 * Web cannot purchase, so there is no paywall to host.
 *
 * Unreachable in practice — the subscription screen checks `isPurchaseSupported` before ever asking
 * for one, and points the pilot at the mobile app instead. Rendering nothing is still the right
 * failure mode if that check is ever missed: a blank area is recoverable, a crash is not.
 *
 * Web still *honours* a subscription bought on a phone; entitlement arrives through the synced
 * `subscriptions/{uid}` doc and involves no billing SDK at all.
 */
@Composable
actual fun ProPaywallHost(
  onPurchaseCompleted: () -> Unit,
  onDismiss: () -> Unit,
) = Unit

@Composable
actual fun CustomerCenterHost(onDismiss: () -> Unit) = Unit
