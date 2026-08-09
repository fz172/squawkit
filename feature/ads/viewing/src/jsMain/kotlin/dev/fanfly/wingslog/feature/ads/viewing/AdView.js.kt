package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize

/**
 * Renders nothing — and unlike the Android and iOS actuals, this is **not a placeholder**. It is the
 * shipping v1 behaviour for the web host: AdMob publishes no browser SDK, so web carries no ads
 * until phase 2 puts it on a Google Ad Manager tag (design §7.3, PRD D5). `isAdsSupported` is
 * likewise a hard `false` on this host, so nothing should reach here in the first place; this is the
 * belt to that braces.
 *
 * It exists so the shared placement code compiles for Kotlin/JS, which is what makes phase 2 a
 * drop-in: web supplies this one actual and changes nothing else (PRD W5).
 */
@Composable
actual fun AdView(
  size: AdUnitSize,
  onFilled: () -> Unit,
  onFailed: (reason: String) -> Unit,
  onClicked: () -> Unit,
  modifier: Modifier,
) {
  // No ad product on web in v1, by decision.
}
