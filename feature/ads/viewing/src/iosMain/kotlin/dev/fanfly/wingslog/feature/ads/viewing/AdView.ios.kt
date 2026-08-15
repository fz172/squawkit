package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize
import platform.UIKit.UIView

/**
 * Real as of P8: a [UIKitView] over the Swift-supplied `GADBannerView`, via [IosAdViewBridge] —
 * `iosApp` has no Podfile, so the SDK arrives as an SPM package Kotlin/Native cannot cinterop
 * against, and Swift owns the view construction (see [IosAdViewBridge]'s KDoc).
 *
 * If no factory was installed — a misconfigured build, or a host that never called
 * `MainEntry.installAdViewFactory` — [IosAdViewBridge.build] returns `null` and this renders
 * nothing, the same collapse path as an unfilled slot on Android (design §7.2).
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
  val currentFilled by rememberUpdatedState(onFilled)
  val currentFailed by rememberUpdatedState(onFailed)
  val currentClicked by rememberUpdatedState(onClicked)
  val adUnitId =
    remember(surface, useTestAds) { adUnitIdFor(surface, useTestAds) }

  UIKitView(
    factory = {
      IosAdViewBridge.build(
        adUnitId = adUnitId,
        sizeId = size.name,
        onFilled = { currentFilled() },
        onFailed = { reason -> currentFailed(reason) },
        onClicked = { currentClicked() },
      ) ?: UIView()
    },
    modifier = modifier.size(
      width = size.widthDp.dp,
      height = size.heightDp.dp,
    ),
  )
}

/**
 * Which inventory to request for [surface]. Mirrors `AdView.android.kt`'s table — same reasoning:
 * developer builds get Google's public test unit, since impressions/clicks from development are
 * invalid traffic and AdMob suspends accounts for it. iOS has its own ids under its own app id
 * (`GADApplicationIdentifier` in Info.plist), separate from Android's.
 */
private fun adUnitIdFor(surface: AdSurface, useTestAds: Boolean): String =
  if (useTestAds) {
    GOOGLE_TEST_BANNER_UNIT_IOS
  } else {
    when (surface) {
      AdSurface.SQUAWKS -> "ca-app-pub-1367143209408464/7354960967"
      AdSurface.TASKS -> "ca-app-pub-1367143209408464/3415715954"
      AdSurface.LOGS -> "ca-app-pub-1367143209408464/4728797628"
    }
  }

/** Google's public test banner unit for iOS — distinct from Android's. */
private const val GOOGLE_TEST_BANNER_UNIT_IOS =
  "ca-app-pub-3940256099942544/2934735716"
