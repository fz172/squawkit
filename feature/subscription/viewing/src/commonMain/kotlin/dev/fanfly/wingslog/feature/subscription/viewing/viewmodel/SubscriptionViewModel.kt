package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.NoOpEntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
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
import kotlin.time.Duration.Companion.milliseconds
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
   * Whether the *manage* flow on this device can actually reach this subscription.
   *
   * Distinct from [isPurchaseSupported], which only asks whether this build has a store at all. A
   * subscriber can hold Pro on a device that cannot manage it — bought on an iPhone, read on
   * Android — and offering a button that opens a Customer Center with nothing in it is worse than
   * saying plainly where the plan lives. See [canManageHere].
   */
  val canManage: Boolean = false,
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
 * Maps the entitlement's `origin_platform` onto a store worth naming.
 *
 * Sourced from the synced entitlement, **not** from the local store SDK. The entitlement is
 * account-scoped, so a pilot who subscribed on an iPhone and later opens the Android app (or the
 * web app, which has no SDK at all) is still correctly told "App Store".
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

/**
 * Whether the [store] this build transacts with can manage a subscription that was billed by
 * [platform].
 *
 * Both sides speak [PurchasePlatform], so the rule is mostly just "same storefront". The exceptions
 * are all deliberate:
 *
 * - A build with no store ([store] `null` — web, or no configured key) manages nothing, whatever
 *   sold the subscription.
 * - A grant with no store behind it ([platform] `null` — a comp) has nothing to cancel anywhere, so
 *   there is no other platform to send the pilot to and the Customer Center handles it gracefully.
 * - [PurchasePlatform.TEST_STORE] is the simulated store developer and dogfood builds transact with.
 *   Its origin never matches a real storefront, so treating that as a mismatch would break managing
 *   every dogfood purchase — which, before GA, is every purchase.
 * - Both Apple storefronts are managed from the same place, so an App Store build handles either.
 *
 * Everything else — Amazon without an Amazon build, a web subscription anywhere — falls out of the
 * equality check as unmanageable, which is correct.
 */
internal fun canManageHere(
  platform: PurchasePlatform?,
  store: PurchasePlatform?
): Boolean = when {
  store == null -> false
  platform == null || platform == PurchasePlatform.TEST_STORE -> true
  platform == PurchasePlatform.MAC_APP_STORE -> store == PurchasePlatform.APP_STORE
  else -> platform == store
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
        store = billingManager.store,
        // Once the entitlement lands, the pending flag is moot — resolve it from the tier rather
        // than trusting a flag to be cleared, so the UI can never stick on "activating".
        isActivating = pending && status != Subscription.Status.STATUS_PRO,
        // Re-read on every emission rather than held: `status()` is auth-scoped, so signing in or
        // out re-runs this. Linking a guest account to a real one does NOT fire authStateChanged
        // (see SettingsViewModel), so an in-session upgrade is reflected when the page is revisited.
        isGuest = authManager.getCurrentUser()?.isAnonymous == true,
      )
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      SubscriptionUiState()
    )

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
      delay(activationGraceMillis.milliseconds)
      // Re-read rather than trusting the flag: by now the webhook has usually landed, and asking
      // the server to re-check an account that is already Pro would burn a provider lookup for
      // nothing.
      if (subscriptionManager.status()
          .first() != Subscription.Status.STATUS_PRO
      ) {
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
  store: PurchasePlatform? = null,
  isActivating: Boolean = false,
  isGuest: Boolean = false,
): SubscriptionUiState {
  val purchasePlatform = purchasePlatformOf(subscription.origin_platform)
  return SubscriptionUiState(
    isPro = status == Subscription.Status.STATUS_PRO,
    lifecycle = subscription.lifecycle,
    willRenew = subscription.will_renew,
    memberSince = subscription.member_since_millis.toDisplayDateOrNull(timeZone),
    currentPeriodEnd = subscription.current_period_end_millis.toDisplayDateOrNull(
      timeZone
    ),
    storageBytesUsed = subscription.storage_bytes_used,
    isPurchaseSupported = isPurchaseSupported,
    isActivating = isActivating,
    purchasePlatform = purchasePlatform,
    canManage = canManageHere(purchasePlatform, store),
    isGuest = isGuest,
  )
}

private fun Long.toDisplayDateOrNull(timeZone: TimeZone): String? =
  if (this <= 0L) {
    null
  } else {
    Instant.fromEpochMilliseconds(this)
      .toLocalDateTime(timeZone)
      .date
      .toDisplayFormat(numberOnly = false)
  }
