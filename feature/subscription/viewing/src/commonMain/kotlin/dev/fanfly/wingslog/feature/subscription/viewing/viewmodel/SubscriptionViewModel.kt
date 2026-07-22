package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Display state for the subscription page. Dates are pre-formatted; storage is formatted in the UI. */
data class SubscriptionUiState(
  val isPro: Boolean = false,
  val lifecycle: Subscription.Lifecycle = Subscription.Lifecycle.LIFECYCLE_NONE,
  val willRenew: Boolean = false,
  /** "Aug 19, 2026" or null when unset. */
  val memberSince: String? = null,
  val currentPeriodEnd: String? = null,
  val storageBytesUsed: Long = 0L,
)

class SubscriptionViewModel(
  subscriptionManager: SubscriptionManager,
) : ViewModel() {

  val uiState: StateFlow<SubscriptionUiState> =
    combine(subscriptionManager.status(), subscriptionManager.entitlement()) { status, subscription ->
      toSubscriptionUiState(status, subscription)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())
}

/** Pure mapping, split out for testing. */
internal fun toSubscriptionUiState(
  status: Subscription.Status,
  subscription: Subscription,
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
): SubscriptionUiState = SubscriptionUiState(
  isPro = status == Subscription.Status.STATUS_PRO,
  lifecycle = subscription.lifecycle,
  willRenew = subscription.will_renew,
  memberSince = subscription.member_since_millis.toDisplayDateOrNull(timeZone),
  currentPeriodEnd = subscription.current_period_end_millis.toDisplayDateOrNull(timeZone),
  storageBytesUsed = subscription.storage_bytes_used,
)

private fun Long.toDisplayDateOrNull(timeZone: TimeZone): String? =
  if (this <= 0L) {
    null
  } else {
    Instant.fromEpochMilliseconds(this)
      .toLocalDateTime(timeZone)
      .date
      .toDisplayFormat(numberOnly = false)
  }
