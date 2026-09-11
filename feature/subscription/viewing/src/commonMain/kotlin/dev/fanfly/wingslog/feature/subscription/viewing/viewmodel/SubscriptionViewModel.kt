package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.subscription.datamanager.EntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.NoOpEntitlementReconciler
import dev.fanfly.wingslog.feature.subscription.datamanager.NoOpPromoCodeRedeemer
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoCodeRedeemer
import dev.fanfly.wingslog.feature.subscription.datamanager.PromoRedemptionResult
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.PROMO_CODE_LENGTH
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import dev.fanfly.wingslog.feature.subscription.model.normalizePromoCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Display state for the subscription page. Dates are pre-formatted; storage is formatted in the UI. */
data class SubscriptionUiState(
  /**
   * No entitlement has been read yet — the page should show a neutral spinner, not a tier.
   *
   * Defaults `true` because the only place this default is ever seen is the `stateIn` seed the
   * page's real StateFlow starts from, before its `combine` has emitted once. Every mapped state
   * (see [toSubscriptionUiState]) sets this `false` explicitly, since a resolved tier — even the
   * free one — is never "still loading". Without this, a returning Pro subscriber briefly sees
   * [isPro] `false` and the free-tier paywall before their real entitlement arrives.
   */
  val isLoading: Boolean = true,
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
   * The store's own management page for this subscription, when the server has learned one (#363).
   *
   * The fallback for every surface [canManage] excludes — above all web, where there is no billing
   * SDK to open a Customer Center with. A link is strictly better than the "go find the device you
   * bought it on" copy, so it wins whenever it is present.
   *
   * `null` covers both "no reconcile has run yet" and "this store exposes no such page" (the Test
   * Store exposes none at all), and both correctly fall back to naming the store.
   */
  val managementUrl: String? = null,
  /**
   * Whether [managementUrl] is our own per-store guess rather than the provider's deep link.
   *
   * Only the caption changes, but the distinction is the honest one: the provider's URL points at
   * *this* subscription, while the derived one is the store's general subscriptions page and cannot
   * promise more than that. Claiming otherwise would be a small lie told to the pilot least able to
   * check it — the one already on the wrong platform.
   */
  val isManagementUrlDerived: Boolean = false,
  /**
   * Pro was granted rather than bought, so there is nothing to manage anywhere.
   *
   * The page shows no manage affordance at all for these — not the Customer Center, not a link, and
   * not the "managed on another platform" notice, whose copy ("open SquawkIt on the device it was
   * purchased with") would be actively false. A comp has no store, no receipt, and no page to send
   * anyone to; the membership card above still reports the tier and its end date, which is the whole
   * truth of the account.
   *
   * Distinct from an *unrecognised* store, which also has no [purchasePlatform] but is a real
   * purchase someone may well need to cancel — that keeps the generic message.
   */
  val isComped: Boolean = false,
  /**
   * Signed in as a guest (anonymous Firebase account), which must not be allowed to subscribe.
   *
   * A guest account cannot be recovered on another device or after a reinstall. Letting one buy a
   * subscription would take the pilot's money and tie the entitlement to an identity they can lose
   * — the charged-and-stranded case, created deliberately rather than by a dropped webhook.
   */
  val isGuest: Boolean = false,
  /**
   * The term a promo code just granted, while its entitlement is still syncing down.
   *
   * `null` at every other moment, including once Pro lands — so it doubles as "the activation in
   * flight came from a code, not the store", which is the only thing separating two otherwise
   * identical waits with very different copy. See [PromoTerm].
   */
  val promoActivationTerm: PromoTerm? = null,
  /**
   * The activation has been pending long enough that "Activating…" has stopped being informative.
   *
   * Keyed on the observable state — still not Pro, this long after paying — and deliberately **not**
   * on whether the reconcile RPC succeeded. Those come apart in both directions: a reconcile that
   * returns `reconciled: false` leaves the pilot equally stuck, and one that fails a second before
   * the webhook lands is no problem at all. What the pilot is asking is "am I Pro yet", so that is
   * what the UI answers.
   *
   * Without this the page span forever: [isActivating] resolves only when the tier flips, so an
   * entitlement that never arrived left "Activating SquawkIt Pro…" on screen indefinitely.
   */
  val isActivationStalled: Boolean = false,
  /**
   * Whether the page should offer promo-code entry at all.
   *
   * True on the paywall, and on a *comped* membership — a second code stacks onto comp time the
   * account still holds, so hiding the entry there would make a term the server supports
   * unreachable. Deliberately false for a store subscriber, whose redemption the server refuses:
   * offering a control that can only be turned down is worse than not offering it.
   */
  val canRedeemPromo: Boolean = false,
  /**
   * Whether this build ships ads at all — the comparison table's "Ad-free experience" row must
   * describe the build the pilot is actually holding, not a hypothetical one (#384). Sourced from
   * [dev.fanfly.wingslog.core.appinfo.AppCapability.isAdsSupported] rather than
   * [dev.fanfly.wingslog.feature.ads.datamanager.AdsManager.showsAds], which additionally reflects
   * *this account's* tier and the developer force-override — neither belongs in a row that is
   * arguing what Free lacks and Pro has.
   */
  val isAdsSupported: Boolean = false,
)

/**
 * The promo-code entry dialog's own state (#750).
 *
 * Held in the ViewModel rather than in the dialog's `remember`, so a half-typed code survives the
 * composition being torn down — a real hazard on Android, where the entry sits behind a system
 * keyboard and, on web, behind a page that re-lays-out on resize. The same rule the form screens
 * follow.
 */
data class PromoCodeUiState(
  val isOpen: Boolean = false,
  /** Canonical, undashed: uppercase, alphabet-cropped, at most [PROMO_CODE_LENGTH]. */
  val code: String = "",
  val isSubmitting: Boolean = false,
  val error: PromoCodeError? = null,
) {
  /** Whether the code is *shaped* like one. The server decides whether it is one. */
  val isComplete: Boolean get() = normalizePromoCode(code) != null
}

/**
 * Why a redemption was refused, in the four flavours that change what the pilot should do next.
 *
 * Distinct from the server's English: the callable's status code is the stable half of that
 * contract, and the copy lives in `strings.xml` where it can be translated.
 */
enum class PromoCodeError {
  /** Unknown, already spent, or past its redemption window — the server does not say which. */
  NOT_VALID,
  TOO_MANY_ATTEMPTS,
  /** Already on a paid store subscription. The code was left unspent. */
  ALREADY_SUBSCRIBED,
  SIGN_IN_REQUIRED,
  /** App Check could not attest this build, so the server refused it. Nothing was spent. */
  APP_UNVERIFIED,
  /** Offline or a failed call. Nothing was spent. */
  UNAVAILABLE,
}

/**
 * The term a redeemed code was worth, reduced to the three the pool is minted in plus a catch-all.
 *
 * An enum rather than the raw day count so the page needs no plural forms for a number it did not
 * choose: the three named terms get copy that reads naturally, and anything else gets a line that
 * names no duration at all. The membership card's end date is the precise answer either way.
 */
enum class PromoTerm {
  ONE_MONTH,
  THREE_MONTHS,
  ONE_YEAR,
  OTHER,
  ;

  internal companion object {
    fun ofDays(days: Int): PromoTerm = when (days) {
      30 -> ONE_MONTH
      90 -> THREE_MONTHS
      365 -> ONE_YEAR
      else -> OTHER
    }
  }
}

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
 * Whether Pro was granted rather than bought, in either of the two ways that can happen.
 *
 * The two do not agree on `source`, which is why both are checked. Our own admin grant
 * (`grantPromoEntitlement`) writes `SOURCE_SERVER_GRANT` with `origin_platform` `"server"`. A promo
 * granted from the RevenueCat dashboard arrives as a *webhook*, and the webhook path always writes
 * `SOURCE_STORE_PURCHASE` — only its `origin_platform` of `"promotional"` gives it away.
 *
 * Deliberately does NOT treat an unrecognised or absent `origin_platform` as a comp. That is a real
 * purchase from a store this client is too old to name, and its owner may genuinely need to cancel
 * it — they get the generic "managed elsewhere" message rather than silence.
 */
internal fun isCompedEntitlement(subscription: Subscription): Boolean =
  subscription.source == Subscription.Source.SOURCE_SERVER_GRANT ||
    subscription.origin_platform == "promotional" ||
    subscription.origin_platform == "server"

/**
 * Whether the [store] this build transacts with can manage a subscription that was billed by
 * [platform].
 *
 * Both sides speak [PurchasePlatform], so the rule is mostly just "same storefront". The exceptions
 * are all deliberate:
 *
 * - A build with no store ([store] `null` — web, or no configured key) manages nothing, whatever
 *   sold the subscription.
 * - A `null` [platform] here means an *unrecognised* store — a purchase written by a newer server
 *   than this client. It is still a real purchase, so the local Customer Center is offered rather
 *   than nothing. Comps never reach this function: [isCompedEntitlement] is checked first and shows
 *   no manage affordance at all.
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
  private val billingManager: BillingManager,
  private val authManager: AuthManager,
  private val appCapability: AppCapability,
  private val entitlementReconciler: EntitlementReconciler = NoOpEntitlementReconciler,
  private val promoCodeRedeemer: PromoCodeRedeemer = NoOpPromoCodeRedeemer,
  /** How long to wait for the webhook before asking the server to re-check. Overridden in tests. */
  private val activationGraceMillis: Long = ACTIVATION_GRACE_MILLIS,
  /** How long before the wait stops being reported as normal. Overridden in tests. */
  private val activationStallMillis: Long = ACTIVATION_STALL_MILLIS,
) : ViewModel() {

  private val purchasePending = MutableStateFlow(false)
  private var activationWatchdog: Job? = null

  /** The activation has outlived [activationStallMillis]; see [SubscriptionUiState.isActivationStalled]. */
  private val activationStalled = MutableStateFlow(false)

  /** The term a just-redeemed code granted, held until the entitlement it bought syncs down. */
  private val promoPending = MutableStateFlow<PromoTerm?>(null)

  private val _promoCodeState = MutableStateFlow(PromoCodeUiState())
  val promoCodeState: StateFlow<PromoCodeUiState> = _promoCodeState.asStateFlow()

  init {
    requestManagementUrlIfMissing()
  }

  /**
   * Ask the server to reconcile when this account holds Pro but has no management URL yet (#363).
   *
   * The URL lives only on RevenueCat's REST subscriber view, never on a webhook event, so a purchase
   * whose webhook worked perfectly is never reconciled and never learns it. The daily scan's other
   * population is *stale* entitlements, and a healthy subscription is by definition not stale — so
   * without this the pilot on the surface that most needs the link (web, which has no billing SDK at
   * all) would wait for their subscription to lapse before getting one.
   *
   * Only asked where the link would actually be used: a build whose own store sold the subscription
   * opens the Customer Center instead and needs nothing. Fire-and-forget, and safe to repeat — the
   * callable is throttled per account server-side, so an account whose provider genuinely reports no
   * URL (the Test Store reports none) costs one cheap rejected call per page visit at worst.
   */
  private fun requestManagementUrlIfMissing() {
    viewModelScope.launch {
      // firstOrNull, not first: an account that never becomes Pro simply has nothing to ask about,
      // and `first` would throw NoSuchElementException the moment the upstream flow completed.
      val subscription = combine(
        subscriptionManager.status(),
        subscriptionManager.entitlement(),
      ) { status, subscription -> status to subscription }
        .firstOrNull { (status, _) -> status == Subscription.Status.STATUS_PRO }
        ?.second ?: return@launch

      // A comp has no store and therefore no URL to learn; asking would burn a provider lookup for
      // an account RevenueCat may never have heard of. The server-side backfill skips them for the
      // same reason.
      val needsLink = !isCompedEntitlement(subscription) &&
        !canManageHere(purchasePlatformOf(subscription.origin_platform), billingManager.store)
      if (needsLink && manageableUrlOrNull(subscription.management_url) == null) {
        entitlementReconciler.reconcileNow()
      }
    }
  }

  val uiState: StateFlow<SubscriptionUiState> =
    combine(
      subscriptionManager.status(),
      subscriptionManager.entitlement(),
      purchasePending,
      promoPending,
      activationStalled,
    ) { status, subscription, pending, promoTerm, stalled ->
      toSubscriptionUiState(
        status = status,
        subscription = subscription,
        isPurchaseSupported = billingManager.isPurchaseSupported,
        store = billingManager.store,
        // Once the entitlement lands, the pending flags are moot — resolve them from the tier
        // rather than trusting a flag to be cleared, so the UI can never stick on "activating".
        isActivating = (pending || promoTerm != null) &&
          status != Subscription.Status.STATUS_PRO,
        promoActivationTerm = promoTerm.takeIf { status != Subscription.Status.STATUS_PRO },
        isActivationStalled = stalled && status != Subscription.Status.STATUS_PRO,
        // Re-read on every emission rather than held: `status()` is auth-scoped, so signing in or
        // out re-runs this. Linking a guest account to a real one does NOT fire authStateChanged
        // (see SettingsViewModel), so an in-session upgrade is reflected when the page is revisited.
        isGuest = authManager.getCurrentUser()?.isAnonymous == true,
        isAdsSupported = appCapability.isAdsSupported,
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
    watchActivation(isPurchase = true)
  }

  /**
   * Runs the two timers behind an in-flight activation: nudge the server, then stop claiming the
   * wait is normal.
   *
   * @param isPurchase the activation came from the store, so the server has something to reconcile
   *   against. A promo grant is already written server-side — RevenueCat has likely never heard of
   *   the account — so it gets the stall timer and no provider lookup.
   */
  private fun watchActivation(isPurchase: Boolean) {
    activationWatchdog?.cancel()
    activationStalled.value = false
    activationWatchdog = viewModelScope.launch {
      delay(activationGraceMillis.milliseconds)
      // Re-read rather than trusting the flag: by now the webhook has usually landed, and asking
      // the server to re-check an account that is already Pro would burn a provider lookup for
      // nothing.
      if (isStillFree()) {
        if (isPurchase) entitlementReconciler.reconcileNow()
        delay((activationStallMillis - activationGraceMillis).coerceAtLeast(0).milliseconds)
        // Deliberately independent of what reconcileNow returned. Its result answers a different
        // question; this one asks only whether the pilot got what they paid for.
        if (isStillFree()) activationStalled.value = true
      }
    }
  }

  /**
   * Re-checks a stalled activation, from the pilot's "Check again".
   *
   * Asks the server to re-check where there is something to re-check, then gives it a short window
   * rather than the full stall period — a manual retry that went quiet for another minute and a half
   * would read as a second failure.
   */
  fun onActivationRecheck() {
    val isPurchase = promoPending.value == null
    activationWatchdog?.cancel()
    activationStalled.value = false
    activationWatchdog = viewModelScope.launch {
      if (isPurchase) entitlementReconciler.reconcileNow()
      delay(activationGraceMillis.milliseconds)
      if (isStillFree()) activationStalled.value = true
    }
  }

  private suspend fun isStillFree(): Boolean =
    subscriptionManager.status().first() != Subscription.Status.STATUS_PRO

  fun onPromoEntryOpened() {
    _promoCodeState.value = PromoCodeUiState(isOpen = true)
  }

  fun onPromoEntryDismissed() {
    _promoCodeState.value = PromoCodeUiState()
  }

  /**
   * Accepts a keystroke into the code field.
   *
   * Cropped to A–Z and 0–9 rather than to the code alphabet: an allowlist that drifted from the
   * server's would silently swallow a *valid* code, which is worse than letting a wrong keystroke
   * through. [normalizePromoCode] is the single source of truth and gates the submit button.
   *
   * Clears any previous error — the pilot is acting on it, and leaving "not valid" under a code
   * they are in the middle of retyping reads as a verdict on the new one.
   */
  fun onPromoCodeChanged(raw: String) {
    val cleaned = raw.uppercase()
      .filter { it in 'A'..'Z' || it in '0'..'9' }
      .take(PROMO_CODE_LENGTH)
    _promoCodeState.update { it.copy(code = cleaned, error = null) }
  }

  /**
   * Sends the code. Grants nothing locally: on success the dialog closes and the page waits for the
   * server-written entitlement to sync, exactly as it waits after a purchase.
   *
   * Guarded against a double submit, because a second in-flight redemption of the same code is at
   * best wasted and at worst a second attempt against the failed-guess budget.
   */
  fun onPromoCodeSubmitted() {
    val current = _promoCodeState.value
    if (current.isSubmitting || !current.isComplete) return

    _promoCodeState.update { it.copy(isSubmitting = true, error = null) }
    viewModelScope.launch {
      when (val result = promoCodeRedeemer.redeem(current.code)) {
        is PromoRedemptionResult.Granted -> {
          _promoCodeState.value = PromoCodeUiState()
          promoPending.value = PromoTerm.ofDays(result.durationDays)
          // The grant is already written server-side, so a wait here is the sync layer, not billing
          // — but it must still end in something other than a permanent spinner.
          watchActivation(isPurchase = false)
        }

        PromoRedemptionResult.NotValid -> failPromo(PromoCodeError.NOT_VALID)
        PromoRedemptionResult.TooManyAttempts -> failPromo(PromoCodeError.TOO_MANY_ATTEMPTS)
        PromoRedemptionResult.AlreadySubscribed -> failPromo(PromoCodeError.ALREADY_SUBSCRIBED)
        PromoRedemptionResult.SignInRequired -> failPromo(PromoCodeError.SIGN_IN_REQUIRED)
        PromoRedemptionResult.AppUnverified -> failPromo(PromoCodeError.APP_UNVERIFIED)
        PromoRedemptionResult.Unavailable -> failPromo(PromoCodeError.UNAVAILABLE)
      }
    }
  }

  /** Keeps the dialog open with the code intact, so a mistyped character can be fixed in place. */
  private fun failPromo(error: PromoCodeError) {
    _promoCodeState.update { it.copy(isSubmitting = false, error = error) }
  }

  private companion object {
    /**
     * Long enough that the webhook round trip — store → provider → webhook → Firestore → sync —
     * has genuinely had its chance, short enough that a pilot who just paid is not left staring.
     * Observed round trips in testing were under two seconds.
     */
    private const val ACTIVATION_GRACE_MILLIS = 10_000L

    /**
     * When the wait stops being reported as normal.
     *
     * Long enough to clear a slow webhook plus a reconcile plus the sync round trip — well past the
     * observed couple of seconds, so an ordinary purchase never sees this. Short enough that a pilot
     * whose entitlement is genuinely never coming is told so while they are still on the page,
     * rather than left with a spinner that had no end state at all.
     */
    private const val ACTIVATION_STALL_MILLIS = 60_000L
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
  promoActivationTerm: PromoTerm? = null,
  isActivationStalled: Boolean = false,
  isGuest: Boolean = false,
  isAdsSupported: Boolean = false,
): SubscriptionUiState {
  val purchasePlatform = purchasePlatformOf(subscription.origin_platform)
  val isComped = isCompedEntitlement(subscription)
  // The provider's deep link first; our per-store page only as a downgrade. A comp gets neither.
  // A simulated Test Store purchase is filtered upstream: the server never persists a URL for one,
  // so `management_url` is empty by the time it reaches here.
  val providerUrl = if (isComped) null else manageableUrlOrNull(subscription.management_url)
  val derivedUrl = if (isComped) null else derivedManagementUrlFor(purchasePlatform)
  return SubscriptionUiState(
    isLoading = false,
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
    promoActivationTerm = promoActivationTerm,
    isActivationStalled = isActivationStalled,
    // A store subscriber is refused server-side; everyone else — free, and comped, who can stack a
    // second term onto the comp time they still hold — is offered the entry.
    canRedeemPromo = !isGuest && (status != Subscription.Status.STATUS_PRO || isComped),
    purchasePlatform = purchasePlatform,
    canManage = !isComped && canManageHere(purchasePlatform, store),
    managementUrl = providerUrl ?: derivedUrl,
    isManagementUrlDerived = providerUrl == null && derivedUrl != null,
    isComped = isComped,
    isGuest = isGuest,
    isAdsSupported = isAdsSupported,
  )
}

/**
 * The synced management URL, if it is one we are willing to open.
 *
 * The server already scheme-checks before persisting, so this is the second of two gates rather than
 * the only one — kept because the value originates with a third party and ends up at a URI handler,
 * and because a doc written by an older server predates that check. Cheap enough to be worth it.
 */
internal fun manageableUrlOrNull(url: String): String? =
  url.trim().takeIf { it.startsWith("https://", ignoreCase = true) }

/**
 * The store's own subscriptions page, used only when the provider reports no `management_url`.
 *
 * A deliberate downgrade, not a substitute. RevenueCat's URL deep-links to the individual
 * subscription; these are the store's general "your subscriptions" pages, and the UI says so with a
 * different caption. They are still worth offering, because every one of them works in a *browser* —
 * a pilot on Android who bought on an iPhone can genuinely cancel from Apple's page here, which is
 * strictly more than the previous answer of "go find the device you bought it on".
 *
 * Two platforms deliberately return `null` rather than a guess:
 *
 * - [PurchasePlatform.WEB] covers Stripe, RC Billing and Paddle, which have no common portal. Their
 *   `management_url` is the only correct destination, and it is reliably present for them.
 * - [PurchasePlatform.TEST_STORE] is simulated; it has no page at all.
 *
 * No Play `?sku=` parameter: that form needs the product id, which the entitlement does not carry.
 * See the PR — adding it server-side to sharpen a fallback that real purchases never reach was not
 * judged worth the wire change.
 */
internal fun derivedManagementUrlFor(platform: PurchasePlatform?): String? = when (platform) {
  PurchasePlatform.PLAY_STORE -> "https://play.google.com/store/account/subscriptions"
  // One Apple account page serves both storefronts, exactly as canManageHere assumes.
  PurchasePlatform.APP_STORE, PurchasePlatform.MAC_APP_STORE ->
    "https://apps.apple.com/account/subscriptions"
  PurchasePlatform.AMAZON -> "https://www.amazon.com/gp/mas/your-account/myapps/yoursubscriptions"
  PurchasePlatform.WEB, PurchasePlatform.TEST_STORE, null -> null
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
