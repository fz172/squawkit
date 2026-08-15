package dev.fanfly.wingslog.feature.ads.datamanager.impl

import android.app.Application
import co.touchlab.kermit.Logger
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.CurrentActivityProvider
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google UMP, run against the foreground [android.app.Activity] via [CurrentActivityProvider] — the
 * CMP form is presented directly on it, and `requestConsentInfoUpdate`/the form-show calls take an
 * `Activity`, not an application `Context`.
 *
 * **[ensureConsent] decides whether an ad request may happen at all; it does not hand the caller a
 * targeting flag to thread through to the request.** Once the CMP flow completes, the Google Mobile
 * Ads SDK reads the on-device TCF consent signals UMP just wrote and applies personalization
 * automatically at request time — a manual "npa" extra on `AdRequest` would only race the SDK's own
 * read of that same signal. That is why `AdView.android.kt` takes no consent parameter: the two SDKs
 * already agree with each other, and this class's only job is to gate the *whether*.
 *
 * The [AdConsentState.PERSONALIZED]/[AdConsentState.NON_PERSONALIZED] distinction returned here is
 * therefore informational (logging/analytics), derived from whether this region required a privacy
 * choice at all rather than parsed out of the raw TCF string. [AdConsentState.DENIED] is the one
 * value with teeth: it means UMP's own [ConsentInformation.canRequestAds] said no, and the caller
 * must not render an ad slot at all.
 */
internal class AndroidAdConsentManager(
  private val application: Application,
  private val activityProvider: CurrentActivityProvider,
  private val appCapability: AppCapability,
  private val developerOptionsManager: DeveloperOptionsManager,
) : AdConsentManager {

  override suspend fun ensureConsent(): AdConsentState {
    val activity = activityProvider.current()
    if (activity == null) {
      log.w { "ensureConsent() called with no foreground activity — falling back to non-personalized" }
      return AdConsentState.NON_PERSONALIZED
    }

    val consentInformation = UserMessagingPlatform.getConsentInformation(application)
    val params = ConsentRequestParameters.Builder()
      .apply {
        // Forces the EEA form path on developer builds so the CMP is exercisable in dev/dogfood
        // without a real EEA device or IP (design §8 "Done when"). UMP silently ignores this debug
        // geography on any physical device it doesn't already recognize as a test device — emulators
        // are exempt, but a real phone needs addTestDeviceHashedId or the form never appears and
        // ensureConsent() resolves as if debug settings were never set at all.
        if (appCapability.isDeveloperOptionsSupported) {
          val testDeviceHashedId = developerOptionsManager.observe().first().adConsentTestDeviceHashedId
          setConsentDebugSettings(
            ConsentDebugSettings.Builder(application)
              .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
              .apply { testDeviceHashedId?.let { addTestDeviceHashedId(it) } }
              .build()
          )
        }
      }
      .build()

    val updateError = suspendCancellableCoroutine<FormError?> { cont ->
      consentInformation.requestConsentInfoUpdate(
        activity,
        params,
        { cont.resume(null) },
        { error -> cont.resume(error) },
      )
    }
    if (updateError != null) {
      log.w { "requestConsentInfoUpdate failed: ${updateError.message}" }
      return AdConsentState.NON_PERSONALIZED
    }

    val formError = suspendCancellableCoroutine<FormError?> { cont ->
      UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error -> cont.resume(error) }
    }
    if (formError != null) {
      log.w { "Consent form failed to load/show: ${formError.message}" }
      return AdConsentState.NON_PERSONALIZED
    }

    return deriveConsentState(
      canRequestAds = consentInformation.canRequestAds(),
      privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
    )
  }

  override suspend fun presentPrivacyOptions() {
    val activity = activityProvider.current()
    if (activity == null) {
      log.w { "presentPrivacyOptions() called with no foreground activity" }
      return
    }
    suspendCancellableCoroutine { cont ->
      UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
        if (error != null) log.w { "showPrivacyOptionsForm failed: ${error.message}" }
        cont.resume(Unit)
      }
    }
  }

  private val log = Logger.withTag("AndroidAdConsentManager")
}

/**
 * Pure mapping, split out for testing (the two inputs otherwise come from a live UMP `Activity`
 * flow, per [AndroidAdConsentManager.ensureConsent]'s KDoc on why the PERSONALIZED/NON_PERSONALIZED
 * split is informational and DENIED is the only value with teeth).
 */
internal fun deriveConsentState(
  canRequestAds: Boolean,
  privacyOptionsRequired: Boolean,
): AdConsentState = when {
  !canRequestAds -> AdConsentState.DENIED
  privacyOptionsRequired -> AdConsentState.NON_PERSONALIZED
  else -> AdConsentState.PERSONALIZED
}
