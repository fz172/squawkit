package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs the subscription status/comparison page. For now it just surfaces the effective tier and the
 * raw entitlement; P4c builds the subscriber status view + non-subscriber comparison + storage row
 * on top of these.
 */
class SubscriptionViewModel(
  subscriptionManager: SubscriptionManager,
) : ViewModel() {

  val status: StateFlow<Subscription.Status> =
    subscriptionManager.status()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Subscription.Status.STATUS_FREE)

  val entitlement: StateFlow<Subscription> =
    subscriptionManager.entitlement()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Subscription())
}
