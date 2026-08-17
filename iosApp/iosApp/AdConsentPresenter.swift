import ComposeApp
import UserMessagingPlatform

/// Real Google UMP, now that P8 links `googleads-mobile-sdk-ios` (which brings
/// `UserMessagingPlatform` in as a transitive SPM dependency — see that package's `Package.swift`).
/// Installs `MainEntry.installConsentInfoUpdateProvider`/`installConsentFormPresenter`/
/// `installAdPrivacyOptionsPresenter`, replacing the ATT-only stub from P7.
///
/// **No ATT prompt, by product decision.** Apple only requires App Tracking Transparency when an
/// app actually reads IDFA / tracks a user across other companies' apps or sites for targeted ads
/// (App Store Review Guideline 5.1.2) — it is not a prerequisite for serving ads at all. Stacking it
/// after the CMP would ask an EEA/UK pilot two separate consent questions back to back for what
/// reads to them as the same thing. A declined ATT prompt already collapsed to `NON_PERSONALIZED`
/// here (identical to a partial CMP decline), so skipping it entirely loses no ad-serving capability
/// — it only forgoes the IDFA-targeted tier ATT approval would have unlocked. iOS therefore never
/// resolves to `PERSONALIZED`; only Android does, via UMP consent alone (no ATT-equivalent gate
/// exists on that platform).
///
/// **The info-update and the actual dialog are two separate providers**, not one, so a caller
/// (onboarding) can resolve whether a privacy choice is needed in the background, put its own
/// priming explanation in front of it, and only then show the real CMP dialog — rather than have it
/// interrupt a pilot mid-scroll the first time an ad slot renders. See `AdConsentManager`'s KDoc.
///
/// `ConsentForm.loadAndPresentIfRequired`/`presentPrivacyOptionsForm` are passed a `nil` view
/// controller deliberately — the SDK itself resolves the top view controller of the key window, the
/// same lookup `NativeGoogleSignInProvider` does by hand for its own sheet.
func installAdConsentProvider() {
    MainEntry.shared.installConsentInfoUpdateProvider { testDeviceHashedId, onResult in
        requestConsentInfoUpdate(testDeviceHashedId: testDeviceHashedId) { error in
            onResult(
                error == nil && ConsentInformation.shared.consentStatus == .required
                    ? "REQUIRED" : "NOT_REQUIRED"
            )
        }
    }

    MainEntry.shared.installConsentFormPresenter { testDeviceHashedId, onResult in
        requestConsentInfoUpdate(testDeviceHashedId: testDeviceHashedId) { error in
            if error != nil {
                onResult("NON_PERSONALIZED")
                return
            }

            ConsentForm.loadAndPresentIfRequired(from: nil) { _ in
                onResult(ConsentInformation.shared.canRequestAds ? "NON_PERSONALIZED" : "DENIED")
            }
        }
    }

    MainEntry.shared.installAdPrivacyOptionsPresenter { onComplete in
        // Google's SDK silently no-ops presentPrivacyOptionsForm when there is nothing to show
        // rather than surfacing an error, so the guard + log below is what makes "the row does
        // nothing" diagnosable at all. Per UMPConsentInformation.h, privacyOptionsRequirementStatus
        // is .unknown (rawValue 0) until requestConsentInfoUpdate has been called at least once
        // *this process* — i.e. until onboarding's background check or an ad slot has actually run
        // — and only defaults to the previous session's cached value once that call has started.
        // .notRequired (rawValue 2) means it resolved, just not to an EEA/UK region requiring one.
        guard ConsentInformation.shared.privacyOptionsRequirementStatus == .required else {
            print(
                "Ad privacy settings: no form to show — " +
                "privacyOptionsRequirementStatus=\(ConsentInformation.shared.privacyOptionsRequirementStatus.rawValue) " +
                "(expected .required == 1). 0 (.unknown) means no ad slot has resolved consent yet " +
                "this run — view an ad-eligible list first. 2 (.notRequired) means it resolved but " +
                "not to a region requiring a privacy choice."
            )
            onComplete()
            return
        }
        ConsentForm.presentPrivacyOptionsForm(from: nil) { error in
            if let error {
                print("Ad privacy settings: presentPrivacyOptionsForm failed: \(error.localizedDescription)")
            }
            onComplete()
        }
    }

    // Backs the Settings row's own visibility (installIsPrivacyOptionsAvailableProvider) — a plain
    // property read, not a CMP call, so this never contacts the CMP for a Pro user either.
    MainEntry.shared.installIsPrivacyOptionsAvailableProvider {
        KotlinBoolean(bool: ConsentInformation.shared.privacyOptionsRequirementStatus == .required)
    }

    // Developer Options' "Reset ad consent" — wipes UMP's on-device cache so the onboarding
    // priming explainer can be re-tested without clearing the app's local data/account.
    MainEntry.shared.installResetConsentAction {
        ConsentInformation.shared.reset()
    }
}

/// Shared by both providers above — always re-run before checking anything, per
/// `UMPConsentInformation.h`'s own guidance ("Must be called in every app session before checking
/// the user's consentStatus or loading a consent form").
private func requestConsentInfoUpdate(testDeviceHashedId: String?, completion: @escaping (Error?) -> Void) {
    let params = RequestParameters()
    #if targetEnvironment(simulator) || DEBUG
    // Debug features are already always-on for the Simulator per DebugSettings' own documentation;
    // the EEA override here is what makes the CMP exercisable in dev/dogfood on a *physical* device
    // too, mirroring AndroidAdConsentManager's debug-geography override. A physical device
    // additionally needs its identifier in testDeviceIdentifiers, or UMP silently ignores the
    // geography override — same failure mode as Android without its registered hash. UMP logs the
    // identifier to the console the first time this runs on an unregistered device (grep the run
    // for "UMPDebugSettings.testDeviceIdentifiers"); paste it into Developer Options' "UMP test
    // device hash" field (Settings → Developer Options) — the same field/flow Android uses, and
    // testDeviceHashedId here is that field's live value.
    let debugSettings = DebugSettings()
    debugSettings.geography = .EEA
    debugSettings.testDeviceIdentifiers = [testDeviceHashedId].compactMap { $0 }
    params.debugSettings = debugSettings
    #endif
    ConsentInformation.shared.requestConsentInfoUpdate(with: params, completionHandler: completion)
}
