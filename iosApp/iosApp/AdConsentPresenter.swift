import ComposeApp
import UserMessagingPlatform

/// Real Google UMP, now that P8 links `googleads-mobile-sdk-ios` (which brings
/// `UserMessagingPlatform` in as a transitive SPM dependency — see that package's `Package.swift`).
/// Installs `MainEntry.installAdConsentProvider`/`installAdPrivacyOptionsPresenter`, replacing the
/// ATT-only stub from P7.
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
/// `ConsentForm.loadAndPresentIfRequired`/`presentPrivacyOptionsForm` are passed a `nil` view
/// controller deliberately — the SDK itself resolves the top view controller of the key window, the
/// same lookup `NativeGoogleSignInProvider` does by hand for its own sheet.
func installAdConsentProvider() {
    MainEntry.shared.installAdConsentProvider { onResult in
        let params = RequestParameters()
        #if targetEnvironment(simulator) || DEBUG
        // Debug features are already always-on for the Simulator per DebugSettings' own
        // documentation; the EEA override here is what makes the CMP exercisable in dev/dogfood on
        // a *physical* device too, mirroring AndroidAdConsentManager's debug-geography override.
        // A physical device additionally needs its identifier in testDeviceIdentifiers, or UMP
        // silently ignores the geography override — same failure mode as Android without its
        // registered hash. UMP logs the identifier to the console the first time this runs on an
        // unregistered device (grep the run for "UMPDebugSettings.testDeviceIdentifiers").
        let debugSettings = DebugSettings()
        debugSettings.geography = .EEA
        debugSettings.testDeviceIdentifiers = knownTestDeviceIdentifiers
        params.debugSettings = debugSettings
        #endif

        ConsentInformation.shared.requestConsentInfoUpdate(with: params) { error in
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
        ConsentForm.presentPrivacyOptionsForm(from: nil) { _ in
            onComplete()
        }
    }
}

/// Physical dev/dogfood devices UMP has been told about (design §8 "Done when"). Add a new one by
/// running once without it listed and grepping the console for the identifier UMP reports.
private let knownTestDeviceIdentifiers = [
    "41D04759-94D5-4CCD-9E1B-509EB6E90639",
]
