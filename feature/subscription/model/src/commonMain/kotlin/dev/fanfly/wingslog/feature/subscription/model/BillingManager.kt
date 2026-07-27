package dev.fanfly.wingslog.feature.subscription.model

import kotlinx.coroutines.flow.Flow

/**
 * The purchase surface for SquawkIt Pro — the client half of subscription_design.html §10 P7.
 *
 * Deliberately platform-neutral and free of any RevenueCat type. The RevenueCat KMP SDK publishes
 * **no Kotlin/JS variant**, and purchasing is an Android/iOS-only capability by product decision
 * (the web app consumes an already-purchased subscription but can never start one). Keeping the
 * contract here — in the all-targets `model` module — lets the web build compile and reason about
 * purchasing without the SDK, backed by [UnsupportedBillingManager].
 *
 * ## This is *not* the entitlement gate
 *
 * Entitlement stays server-authoritative (design §1/§3): the only writer of `subscriptions/{uid}` is
 * a Cloud Function, and [dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager]
 * remains the single gate the app enforces on. A purchase made here reaches that gate the long way
 * round — RevenueCat validates the receipt, posts a webhook, the function normalizes it through
 * `applyEntitlement`, and the synced doc lands back in the local store. That round trip is what
 * makes "pay once, Pro everywhere" (including web) structural rather than per-device client state.
 *
 * So a [BillingManager] implementation must never grant entitlement locally. [customerInfo] exists to
 * *reflect* purchase state (and to let the UI wait for the webhook to land), not to unlock features.
 */
interface BillingManager {

  /**
   * Whether this build can start a purchase at all. `false` on web and on any host with no
   * configured store key — callers should render "manage your subscription on iOS or Android"
   * rather than a buy button.
   */
  val isPurchaseSupported: Boolean

  /**
   * The store this build buys and manages through, so the UI can tell a subscriber whether the
   * *manage* flow will actually reach their subscription.
   *
   * Entitlement is account-scoped, so a pilot who subscribed on an iPhone sees Pro in the Android
   * app — but neither the store nor RevenueCat's Customer Center can cancel or change a plan sold
   * by a different store. Comparing this against the entitlement's origin platform is what
   * separates "manage it here" from "go back to the device you bought it on".
   */
  val store: BillingStore

  /**
   * The Pro offering (its packages and localized prices) for a custom purchase UI, or
   * [ProOffering.Unavailable] when offerings can't be loaded. Prefer the RevenueCat-hosted paywall
   * for the primary flow; this backs the fallback/native UI and the price shown in upsell copy.
   */
  suspend fun proOffering(): ProOffering

  /** Starts the store purchase flow for [pkg]. Never throws; user cancellation is a normal outcome. */
  suspend fun purchase(pkg: ProPackage): PurchaseOutcome

  /** Restores prior purchases on this store account (App Store / Play requirement). */
  suspend fun restorePurchases(): PurchaseOutcome

  /**
   * The store's view of the customer, refreshed as the SDK sees changes. Used to detect that a
   * purchase completed and to show "waiting to activate" while the webhook lands. Emits
   * [StoreCustomerInfo.Unknown] where purchasing is unsupported.
   */
  fun customerInfo(): Flow<StoreCustomerInfo>

  /**
   * Binds the store identity to the signed-in SquawkIt account so RevenueCat's webhook carries a uid
   * the backend can write an entitlement for. Called by the identity coordinator on auth changes;
   * pass `null` on sign-out.
   */
  suspend fun setAppUserId(uid: String?)
}

/**
 * The store a build transacts with.
 *
 * Deliberately not "the platform": what matters for managing a subscription is the storefront that
 * billed it, and an Android build sold through Google Play cannot manage an Amazon Appstore
 * subscription any more than it can manage an App Store one.
 */
enum class BillingStore {
  PLAY_STORE,
  APP_STORE,

  /** No store: web, or a build with no configured key. Nothing can be bought or managed here. */
  NONE,
}

/** The identifier of the entitlement configured in the RevenueCat dashboard. */
const val PRO_ENTITLEMENT_ID: String = "SquawkIt Pro"

/** The RevenueCat offering whose packages back the paywall. */
const val PRO_OFFERING_ID: String = "default"

/** A purchasable Pro plan, normalized away from the store SDK's package/product types. */
data class ProPackage(
  /** RevenueCat package identifier — the handle the SDK purchases by. */
  val id: String,
  val period: BillingPeriod,
  /** Store-localized price, already formatted for display (e.g. "$49.99"). */
  val formattedPrice: String,
  /** Localized price normalized per month, for "billed yearly, $X/mo" copy. `null` when unknown. */
  val formattedPricePerMonth: String?,
)

/** The billing periods SquawkIt Pro is sold in (subscription PRD product configuration). */
enum class BillingPeriod { MONTHLY, YEARLY, OTHER }

/** The Pro offering, or the reason it isn't purchasable right now. */
sealed interface ProOffering {
  data class Available(val packages: List<ProPackage>) : ProOffering {
    /** Yearly is the promoted default when present. */
    val preferred: ProPackage?
      get() = packages.firstOrNull { it.period == BillingPeriod.YEARLY } ?: packages.firstOrNull()
  }

  /** No offering — misconfigured dashboard, offline, or a build without purchasing. */
  data class Unavailable(val reason: BillingError) : ProOffering
}

/** The outcome of a purchase or restore. */
sealed interface PurchaseOutcome {
  /**
   * The store accepted the purchase. Entitlement is **not** yet in force: the app must wait for the
   * webhook-written entitlement to sync before treating the account as Pro.
   */
  data object Purchased : PurchaseOutcome

  /** The user backed out. Not an error — never show an error UI for this. */
  data object Cancelled : PurchaseOutcome

  data class Failed(val error: BillingError) : PurchaseOutcome
}

/**
 * Billing failures the UI distinguishes. Store SDK error codes collapse into these, because the only
 * thing the UI varies on is what it tells the pilot to do next.
 */
enum class BillingError {
  /** No network, or the store/RevenueCat backend was unreachable. Retryable. */
  NETWORK,

  /** The store rejected the purchase (payment declined, purchases disabled on the device). */
  STORE_PROBLEM,

  /** Nothing to restore for this store account. */
  NOTHING_TO_RESTORE,

  /** This build/platform cannot purchase (web, or no store key configured). */
  UNSUPPORTED,

  /** Anything else — offering misconfigured, unexpected SDK error. */
  UNKNOWN,
}

/** The store's view of the signed-in customer. */
sealed interface StoreCustomerInfo {
  /** Purchasing unsupported, or the SDK hasn't reported yet. */
  data object Unknown : StoreCustomerInfo

  data class Known(
    /** Whether the store currently considers [PRO_ENTITLEMENT_ID] active. */
    val hasActiveProEntitlement: Boolean,
    /** Deep link to the platform's manage-subscription page, when the store provides one. */
    val managementUrl: String?,
  ) : StoreCustomerInfo
}

/**
 * The binding used wherever purchasing cannot happen — the web app, and any host without a store
 * key. Every call is a well-defined no-op so callers need no platform branches; only
 * [isPurchaseSupported] has to be consulted to decide what UI to draw.
 */
object UnsupportedBillingManager : BillingManager {
  override val isPurchaseSupported: Boolean = false

  override val store: BillingStore = BillingStore.NONE

  override suspend fun proOffering(): ProOffering = ProOffering.Unavailable(BillingError.UNSUPPORTED)

  override suspend fun purchase(pkg: ProPackage): PurchaseOutcome =
    PurchaseOutcome.Failed(BillingError.UNSUPPORTED)

  override suspend fun restorePurchases(): PurchaseOutcome =
    PurchaseOutcome.Failed(BillingError.UNSUPPORTED)

  override fun customerInfo(): Flow<StoreCustomerInfo> =
    kotlinx.coroutines.flow.flowOf(StoreCustomerInfo.Unknown)

  override suspend fun setAppUserId(uid: String?) = Unit
}
