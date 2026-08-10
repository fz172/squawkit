package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize

/**
 * P1 placeholder: renders nothing.
 *
 * P8 replaces this with a `UIKitView` over a **Swift-supplied factory**, not a direct
 * `GADBannerView` reference — `iosApp` has no Podfile, so dependencies arrive via Swift Package
 * Manager and there is no cinterop to see the SDK from Kotlin/Native. The pattern already exists in
 * `MainViewController.kt`: `installAppCheckTokenProvider` hands Kotlin a Swift-owned capability
 * through a bridge, for exactly this reason.
 *
 * The render-nothing behaviour below is also the permanent fallback for when no factory has been
 * installed, so a misconfigured build degrades to ad-free rather than crashing.
 */
@Composable
actual fun AdView(
  size: AdUnitSize,
  surface: AdSurface,
  useTestAds: Boolean,
  onFilled: () -> Unit,
  onFailed: (reason: String) -> Unit,
  onClicked: () -> Unit,
  modifier: Modifier,
) {
  // Intentionally empty until P8.
}
