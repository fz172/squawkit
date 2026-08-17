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
 * `MainEntry.installAdConsentProvider`/`installAdPrivacyOptionsPresenter`, exactly as
 * `IosAppCheckBridge` does for the App Check token. Left uninstalled → falls back to
 * [AdConsentState.NON_PERSONALIZED] / a no-op, the same "degrade rather than crash" shape as every
 * other iOS ads actual (design §7.2). No ATT here by product decision — see
 * `iosApp/AdConsentPresenter.swift`.
 */
object IosAdConsentBridge {
  private var consentProvider: ((onResult: (String) -> Unit) -> Unit)? = null
  private var privacyOptionsPresenter: ((onComplete: () -> Unit) -> Unit)? = null
  private var privacyOptionsAvailableProvider: (() -> Boolean)? = null

  fun installConsentProvider(provider: (onResult: (String) -> Unit) -> Unit) {
    consentProvider = provider
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

  internal suspend fun ensureConsent(): AdConsentState {
    val provider = consentProvider
    if (provider == null) {
      log.w { "ensureConsent() requested but no provider installed (iOS host didn't wire it)" }
      return AdConsentState.NON_PERSONALIZED
    }
    val result = withTimeoutOrNull(RESOLUTION_TIMEOUT) {
      suspendCancellableCoroutine { cont -> provider { raw -> cont.resume(raw) } }
    }
    if (result == null) {
      log.w { "ensureConsent() timed out after $RESOLUTION_TIMEOUT" }
      return AdConsentState.NON_PERSONALIZED
    }
    return runCatching { AdConsentState.valueOf(result) }
      .getOrElse {
        log.w { "Swift returned an unrecognised consent state: $result" }
        AdConsentState.NON_PERSONALIZED
      }
  }

  internal fun isPrivacyOptionsAvailable(): Boolean = privacyOptionsAvailableProvider?.invoke() ?: false

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
