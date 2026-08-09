package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize

/**
 * P1 placeholder: renders nothing. Google Mobile Ads arrives at P5, as an `AndroidView` wrapping
 * `AdView` with a fixed `AdSize.BANNER` and `MobileAds.initialize()` called lazily on the first
 * `showsAds() == true` — never at app start, so a Heavy user's app never starts an ad SDK.
 *
 * Reports nothing rather than calling [onFailed]: no request was attempted, so claiming a fill
 * failure would put a fabricated `ad_fill_failed` on every slot in every build.
 */
@Composable
actual fun AdView(
  size: AdUnitSize,
  onFilled: () -> Unit,
  onFailed: (reason: String) -> Unit,
  onClicked: () -> Unit,
  modifier: Modifier,
) {
  // Intentionally empty until P5.
}
