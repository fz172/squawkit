package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.NoOpEntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_amazon
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_mac_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_play_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_test_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_web
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
  /**
   * Whether this build can start a purchase. False on web, which consumes a subscription bought on
   * a phone but can never buy one, and in a release build with no store key yet.
   */
  val isPurchaseSupported: Boolean = false,
  /**
   * The store took payment but the entitlement has not synced back yet.
   *
   * This gap is inherent to a server-authoritative entitlement: the purchase becomes Pro only once
   * RevenueCat's webhook has written `subscriptions/{uid}` and that doc has synced down. It is
   * normally a second or two. Showing it beats either lying (flipping to Pro locally, which the
   * design forbids) or appearing to have done nothing after the pilot paid.
   */
  val isActivating: Boolean = false,
  /**
   * Where the subscription was bought, so the pilot knows where to cancel it. `null` when there is
   * no store to name — a comped account, or a platform we don't recognise.
   */
  val purchasePlatform: PurchasePlatform? = null,
  /**
   * Signed in as a guest (anonymous Firebase account), which must not be allowed to subscribe.
   *
   * A guest account cannot be recovered on another device or after a reinstall. Letting one buy a
   * subscription would take the pilot's money and tie the entitlement to an identity they can lose
   * — the charged-and-stranded case, created deliberately rather than by a dropped webhook.
   */
  val isGuest: Boolean = false,
)

/**
 * The store that billed the subscription, as shown on the status page.
 *
 * Sourced from the synced entitlement's `origin_platform`, **not** from the local store SDK. The
 * entitlement is account-scoped, so a pilot who subscribed on an iPhone and later opens the Android
 * app (or the web app, which has no SDK at all) is still correctly told "App Store".
 *
 * The web billers collapse into one [WEB] entry because they all cancel in the same place from the
 * pilot's point of view; the app stores deliberately do not, because they don't.
 */
enum class PurchasePlatform(val labelRes: StringResource) {
  APP_STORE(Res.string.subscription_platform_app_store),
  MAC_APP_STORE(Res.string.subscription_platform_mac_app_store),
  PLAY_STORE(Res.string.subscription_platform_play_store),
  AMAZON(Res.string.subscription_platform_amazon),
  WEB(Res.string.subscription_platform_web),
  TEST_STORE(Res.string.subscription_platform_test_store),
}

/**
 * Maps the entitlement's `origin_platform` onto a store worth naming.
 *
 * `null` means "show no row at all" rather than "show Unknown": a comped or server-granted account
 * has nothing to cancel, and printing the word "unknown" to a paying subscriber tells them nothing
 * and looks broken. Anything unrecognised — including a value written by a newer server than this
 * client — falls into the same silent bucket.
 */
internal fun purchasePlatformOf(originPlatform: String): PurchasePlatform? =
  when (originPlatform) {
    "app_store" -> PurchasePlatform.APP_STORE
    "mac_app_store" -> PurchasePlatform.MAC_APP_STORE
    "play_store" -> PurchasePlatform.PLAY_STORE
    "amazon" -> PurchasePlatform.AMAZON
    "stripe", "rc_billing", "paddle" -> PurchasePlatform.WEB
    "test_store" -> PurchasePlatform.TEST_STORE
    // "promotional" and "server" are grants, not purchases — nothing to cancel.
    else -> null
  }

class SubscriptionViewModel(
  private val subscriptionManager: SubscriptionManager,
  billingManager: BillingManager,
  private val authManager: AuthManager,
  private val entitlementReconciler: EntitlementReconciler = NoOpEntitlementReconciler,
  /** How long to wait for the webhook before asking the server to re-check. Overridden in tests. */
  private val activationGraceMillis: Long = ACTIVATION_GRACE_MILLIS,
) : ViewModel() {

  private val purchasePending = MutableStateFlow(false)
  private var activationWatchdog: Job? = null

  val uiState: StateFlow<SubscriptionUiState> =
    combine(
      subscriptionManager.status(),
      subscriptionManager.entitlement(),
      purchasePending,
    ) { status, subscription, pending ->
      toSubscriptionUiState(
        status = status,
        subscription = subscription,
        isPurchaseSupported = billingManager.isPurchaseSupported,
        // Once the entitlement lands, the pending flag is moot — resolve it from the tier rather
        // than trusting a flag to be cleared, so the UI can never stick on "activating".
        isActivating = pending && status != Subscription.Status.STATUS_PRO,
        // Re-read on every emission rather than held: `status()` is auth-scoped, so signing in or
        // out re-runs this. Linking a guest account to a real one does NOT fire authStateChanged
        // (see SettingsViewModel), so an in-session upgrade is reflected when the page is revisited.
        isGuest = authManager.getCurrentUser()?.isAnonymous == true,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())

  /**
   * The store accepted a purchase; wait for the entitlement webhook to land.
   *
   * Also arms a watchdog. Normally the webhook writes the entitlement and it syncs down within a
   * second or two — but when that never happens, the pilot has been charged and is left watching
   * "Activating SquawkIt Pro…" indefinitely. The daily reconciler cannot rescue them either: an
   * account that never got Pro has nothing stale for a scan to find. So after a grace period, ask
   * the server to re-check this account against the provider directly.
   *
   * Fire-and-forget by design. It asks a question the server answers authoritatively; the entitlement
   * still arrives through the normal synced path, so a failed or throttled call changes nothing the
   * pilot can see.
   */
  fun onPurchaseCompleted() {
    purchasePending.value = true
    activationWatchdog?.cancel()
    activationWatchdog = viewModelScope.launch {
      delay(activationGraceMillis)
      // Re-read rather than trusting the flag: by now the webhook has usually landed, and asking
      // the server to re-check an account that is already Pro would burn a provider lookup for
      // nothing.
      if (subscriptionManager.status().first() != Subscription.Status.STATUS_PRO) {
        entitlementReconciler.reconcileNow()
      }
    }
  }

  private companion object {
    /**
     * Long enough that the webhook round trip — store → provider → webhook → Firestore → sync —
     * has genuinely had its chance, short enough that a pilot who just paid is not left staring.
     * Observed round trips in testing were under two seconds.
     */
    private const val ACTIVATION_GRACE_MILLIS = 10_000L
  }
}

/** Pure mapping, split out for testing. */
internal fun toSubscriptionUiState(
  status: Subscription.Status,
  subscription: Subscription,
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
  isPurchaseSupported: Boolean = false,
  isActivating: Boolean = false,
  isGuest: Boolean = false,
): SubscriptionUiState = SubscriptionUiState(
  isPro = status == Subscription.Status.STATUS_PRO,
  lifecycle = subscription.lifecycle,
  willRenew = subscription.will_renew,
  memberSince = subscription.member_since_millis.toDisplayDateOrNull(timeZone),
  currentPeriodEnd = subscription.current_period_end_millis.toDisplayDateOrNull(timeZone),
  storageBytesUsed = subscription.storage_bytes_used,
  isPurchaseSupported = isPurchaseSupported,
  isActivating = isActivating,
  purchasePlatform = purchasePlatformOf(subscription.origin_platform),
  isGuest = isGuest,
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
