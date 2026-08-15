import ComposeApp
import AppTrackingTransparency
import UserMessagingPlatform

/// Real Google UMP + ATT, now that P8 links `googleads-mobile-sdk-ios` (which brings
/// `UserMessagingPlatform` in as a transitive SPM dependency — see that package's `Package.swift`).
/// Installs `MainEntry.installAdConsentProvider`/`installAdPrivacyOptionsPresenter`, replacing the
/// ATT-only stub from P7.
///
/// Order matters (design §8): the CMP resolves before ATT is ever requested, since a pilot who
/// hasn't cleared consent shouldn't be prompted for tracking permission a moment later for the same
/// underlying purpose. `ConsentForm.loadAndPresentIfRequired`/`presentPrivacyOptionsForm` are
/// passed a `nil` view controller deliberately — the SDK itself resolves the top view controller of
/// the key window, the same lookup `NativeGoogleSignInProvider` does by hand for its own sheet.
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
                requestTrackingAuthorization { authorized in
                    onResult(authorized ? "PERSONALIZED" : "NON_PERSONALIZED")
                }
                return
            }

            ConsentForm.loadAndPresentIfRequired(from: nil) { _ in
                guard ConsentInformation.shared.canRequestAds else {
                    onResult("DENIED")
                    return
                }
                requestTrackingAuthorization { authorized in
                    let privacyChoiceRequired =
                        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
                    onResult(
                        (privacyChoiceRequired || !authorized) ? "NON_PERSONALIZED" : "PERSONALIZED"
                    )
                }
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

private func requestTrackingAuthorization(onResult: @escaping (Bool) -> Void) {
    ATTrackingManager.requestTrackingAuthorization { status in
        DispatchQueue.main.async {
            onResult(status == .authorized)
        }
    }
}
