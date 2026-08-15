import ComposeApp
import GoogleMobileAds
import UIKit

/// Owns one `BannerView` and its delegate, so the ad view survives as long as Kotlin's `UIKitView`
/// holds a reference to `bannerView` (via `objc_setAssociatedObject` in `installAdViewFactory`'s
/// closure) — `BannerView.delegate` is weak, so nothing else keeps this alive.
///
/// See `IosAdViewBridge`'s KDoc: `iosApp` has no Podfile, so Google Mobile Ads arrives as an SPM
/// package Kotlin/Native cannot cinterop against, and this class is the Swift half of that seam.
private final class BannerAdCoordinator: NSObject, BannerViewDelegate {
    let bannerView: BannerView
    private let onFilled: () -> Void
    private let onFailed: (String) -> Void
    private let onClicked: () -> Void

    init(
        adUnitId: String,
        adSize: AdSize,
        rootViewController: UIViewController?,
        onFilled: @escaping () -> Void,
        onFailed: @escaping (String) -> Void,
        onClicked: @escaping () -> Void
    ) {
        self.bannerView = BannerView(adSize: adSize)
        self.onFilled = onFilled
        self.onFailed = onFailed
        self.onClicked = onClicked
        super.init()
        bannerView.adUnitID = adUnitId
        bannerView.rootViewController = rootViewController
        bannerView.delegate = self
    }

    func load() {
        bannerView.load(Request())
    }

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        onFilled()
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        // Coarse and non-identifying, same as the Android actual: this becomes an analytics param
        // the pilot never sees, and a fill failure is a normal quiet outcome (N3).
        onFailed(String((error as NSError).code))
    }

    func bannerViewDidRecordClick(_ bannerView: BannerView) {
        onClicked()
    }
}

private var associatedCoordinatorKey: UInt8 = 0

/// Installs `MainEntry.installAdViewFactory`. Called once at app startup, alongside the other
/// bridge installs in `iosApp.swift`.
func installAdViewFactory() {
    MobileAds.shared.start(completionHandler: nil)

    MainEntry.shared.installAdViewFactory { adUnitId, sizeId, onFilled, onFailed, onClicked in
        // Fixed sizes only, matching AdUnitSize/AdView.android.kt — no inline-adaptive banner, so
        // the no-billboard guarantee (design G10) holds by construction on this platform too.
        let adSize = sizeId == "LARGE_BANNER" ? AdSizeLargeBanner : AdSizeBanner
        let coordinator = BannerAdCoordinator(
            adUnitId: adUnitId,
            adSize: adSize,
            rootViewController: rootViewControllerForAds(),
            // Kotlin's Unit-returning closures bridge to Swift as `() -> KotlinUnit`, not `Void`;
            // wrapping discards that return value so these satisfy `@escaping () -> Void`.
            onFilled: { onFilled() },
            onFailed: { reason in onFailed(reason) },
            onClicked: { onClicked() }
        )
        // The banner view is what Kotlin's UIKitView actually holds onto; tying the coordinator's
        // (and therefore the delegate's) lifetime to it is what keeps callbacks firing.
        objc_setAssociatedObject(
            coordinator.bannerView,
            &associatedCoordinatorKey,
            coordinator,
            .OBJC_ASSOCIATION_RETAIN_NONATOMIC
        )
        coordinator.load()
        return coordinator.bannerView
    }
}

/// Same key-window lookup `NativeGoogleSignInProvider` uses to find a view controller to present
/// over — a banner needs one too, for the full-screen overlay a click can open.
private func rootViewControllerForAds() -> UIViewController? {
    UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .flatMap(\.windows)
        .first(where: \.isKeyWindow)?
        .rootViewController
}
