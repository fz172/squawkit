package dev.fanfly.wingslog.feature.ads.viewing

import dev.fanfly.wingslog.feature.ads.viewing.IosAdViewBridge.build
import platform.UIKit.UIView

/**
 * Bridge for the iOS ad creative. Google Mobile Ads has no cinterop path here — `iosApp` has no
 * Podfile, dependencies arrive as SPM packages, and there is no Kotlin CocoaPods plugin to see
 * `GADBannerView` from Kotlin/Native — so Swift owns a factory that builds a configured banner
 * view and hands it back as a plain `UIView`, mirroring `IosAdConsentBridge`/`IosAppCheckBridge`.
 *
 * [sizeId] is [dev.fanfly.wingslog.feature.ads.model.AdUnitSize.name] ("BANNER" or
 * "LARGE_BANNER") rather than a marshalled `CGSize`: both values are already Google's own named
 * fixed banner sizes (320×50 / 320×100), so Swift maps the id straight to the matching constant
 * instead of Kotlin reconstructing one.
 *
 * Left uninstalled → [build] returns `null` and [AdView] renders nothing, the same collapse path
 * as an unfilled slot — a misconfigured build degrades to ad-free rather than crashing (design §7.2).
 */
object IosAdViewBridge {

  private var factory: ((
    adUnitId: String,
    sizeId: String,
    onFilled: () -> Unit,
    onFailed: (reason: String) -> Unit,
    onClicked: () -> Unit,
  ) -> UIView?)? = null

  fun install(
    factory: (
      adUnitId: String,
      sizeId: String,
      onFilled: () -> Unit,
      onFailed: (reason: String) -> Unit,
      onClicked: () -> Unit,
    ) -> UIView?
  ) {
    this.factory = factory
  }

  internal fun build(
    adUnitId: String,
    sizeId: String,
    onFilled: () -> Unit,
    onFailed: (reason: String) -> Unit,
    onClicked: () -> Unit,
  ): UIView? = factory?.invoke(adUnitId, sizeId, onFilled, onFailed, onClicked)
}
