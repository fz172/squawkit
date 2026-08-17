package dev.fanfly.wingslog.feature.ads.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Bridge for iOS ad consent. UMP is a Swift-side concern — it has no cinterop path here (`iosApp`
 * has no Podfile; SPM only) — so the host installs providers via
 * `MainEntry.installConsentInfoUpdateProvider`/`installConsentFormPresenter`/
 * `installAdPrivacyOptionsPresenter`, exactly as `IosAppCheckBridge` does for the App Check token.
 * Left uninstalled → falls back to
 * [AdConsentState.NON_PERSONALIZED] / a no-op, the same "degrade rather than crash" shape as every
 * other iOS ads actual (design §7.2). No ATT here by product decision — see
 * `iosApp/AdConsentPresenter.swift`.
 */
object IosAdConsentBridge {
  private var consentInfoUpdateProvider: ((testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit)? =
    null
  private var consentFormPresenter: ((testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit)? = null
  private var privacyOptionsPresenter: ((onComplete: () -> Unit) -> Unit)? = null
  private var privacyOptionsAvailableProvider: (() -> Boolean)? = null
  private var resetConsentAction: (() -> Unit)? = null

  /**
   * [provider] runs `ConsentInformation.shared.requestConsentInfoUpdate` only — no UI — and calls
   * back with `"REQUIRED"`/`"NOT_REQUIRED"`. Backs [isConsentRequired]; see that KDoc and
   * [AdConsentManager]'s for why the background check is split from actually presenting anything.
   * [testDeviceHashedId] is the Developer Options "UMP test device hash" (null if unset, or the
   * build has no Developer Options) — the same field [AndroidAdConsentManager] reads on Android.
   */
  fun installConsentInfoUpdateProvider(provider: (testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit) {
    consentInfoUpdateProvider = provider
  }

  /** [provider] resolves consent info fresh and shows the CMP dialog if required — backs [presentConsentForm]. */
  fun installConsentFormPresenter(presenter: (testDeviceHashedId: String?, onResult: (String) -> Unit) -> Unit) {
    consentFormPresenter = presenter
  }

  fun installPrivacyOptionsPresenter(presenter: (onComplete: () -> Unit) -> Unit) {
    privacyOptionsPresenter = presenter
  }

  /**
   * [provider] reads `ConsentInformation.shared.privacyOptionsRequirementStatus` synchronously —
   * a property read, not an SDK call — so unlike the two providers above this needs no
   * callback/timeout plumbing.
   */
  fun installPrivacyOptionsAvailableProvider(provider: () -> Boolean) {
    privacyOptionsAvailableProvider = provider
  }

  /** [action] calls `ConsentInformation.shared.reset()` — developer-only, backs [resetConsent]. */
  fun installResetConsentAction(action: () -> Unit) {
    resetConsentAction = action
  }

  internal suspend fun isConsentRequired(testDeviceHashedId: String?): Boolean {
    val provider = consentInfoUpdateProvider
    if (provider == null) {
      log.w { "isConsentRequired() requested but no provider installed (iOS host didn't wire it)" }
      return false
    }
    val result = withTimeoutOrNull(RESOLUTION_TIMEOUT) {
      suspendCancellableCoroutine { cont -> provider(testDeviceHashedId) { raw -> cont.resume(raw) } }
    }
    if (result == null) {
      log.w { "isConsentRequired() timed out after $RESOLUTION_TIMEOUT" }
      return false
    }
    return result == "REQUIRED"
  }

  internal suspend fun presentConsentForm(testDeviceHashedId: String?): AdConsentState {
    val presenter = consentFormPresenter
    if (presenter == null) {
      log.w { "presentConsentForm() requested but no presenter installed (iOS host didn't wire it)" }
      return AdConsentState.NON_PERSONALIZED
    }
    val result = withTimeoutOrNull(RESOLUTION_TIMEOUT) {
      suspendCancellableCoroutine { cont -> presenter(testDeviceHashedId) { raw -> cont.resume(raw) } }
    }
    if (result == null) {
      log.w { "presentConsentForm() timed out after $RESOLUTION_TIMEOUT" }
      return AdConsentState.NON_PERSONALIZED
    }
    return runCatching { AdConsentState.valueOf(result) }
      .getOrElse {
        log.w { "Swift returned an unrecognised consent state: $result" }
        AdConsentState.NON_PERSONALIZED
      }
  }

  internal fun isPrivacyOptionsAvailable(): Boolean = privacyOptionsAvailableProvider?.invoke() ?: false

  internal fun resetConsent() {
    val action = resetConsentAction
    if (action == null) {
      log.w { "resetConsent() requested but no action installed (iOS host didn't wire it)" }
      return
    }
    action()
  }

  internal suspend fun presentPrivacyOptions() {
    val presenter = privacyOptionsPresenter
    if (presenter == null) {
      log.w { "presentPrivacyOptions() requested but no presenter installed (iOS host didn't wire it)" }
      return
    }
    withTimeoutOrNull(RESOLUTION_TIMEOUT) {
      suspendCancellableCoroutine { cont -> presenter { cont.resume(Unit) } }
    }
  }

  private val log = Logger.withTag("IosAdConsentBridge")
  private val RESOLUTION_TIMEOUT = 30.seconds
}
