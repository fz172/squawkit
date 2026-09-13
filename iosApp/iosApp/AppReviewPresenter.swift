import ComposeApp
import StoreKit
import UIKit

/// StoreKit's in-app review card, for Settings → About → "Rate SquawkIt". `AppStore.requestReview`
/// is Swift-only, hence a bridge rather than a Kotlin/Native call (`IosAppReviewBridge`). Apple
/// decides whether the card actually shows (at most three a year per device in production; always
/// in a development build, never in TestFlight) and reports nothing back, so the return value only
/// says the request was made — not that a pilot saw anything.
func installAppReviewRequester() {
    MainEntry.shared.installAppReviewRequester {
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
        else {
            return KotlinBoolean(bool: false)
        }
        AppStore.requestReview(in: scene)
        return KotlinBoolean(bool: true)
    }
}
