package dev.fanfly.wingslog.feature.subscription.viewing.paywall

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.subscription.billing.ui.RevenueCatCustomerCenter
import dev.fanfly.wingslog.feature.subscription.billing.ui.RevenueCatProPaywall

@Composable
actual fun ProPaywallHost(
  onPurchaseCompleted: () -> Unit,
  onDismiss: () -> Unit,
) {
  RevenueCatProPaywall(onPurchaseCompleted = onPurchaseCompleted, onDismiss = onDismiss)
}

@Composable
actual fun CustomerCenterHost(onDismiss: () -> Unit) {
  RevenueCatCustomerCenter(onDismiss = onDismiss)
}
