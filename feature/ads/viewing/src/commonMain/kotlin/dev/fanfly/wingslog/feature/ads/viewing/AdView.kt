package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize

/**
 * The single seam between shared placement code and an ad product (design §7, N4).
 *
 * Everything that makes an ad card an ad card — the "Sponsored" label, the band layout, the
 * "Remove ads with Heavy" link, collapsing to zero height when unfilled, the analytics events —
 * belongs to the common `AdSlot` above this. This composable owns nothing but the creative, which
 * is what lets AdMob serve Android and iOS while a Google Ad Manager tag serves web in phase 2
 * without reopening the design.
 *
 * Note what is *absent*: there is no targeting parameter. A tail number, squawk text or account id
 * cannot reach an ad request through this signature even by accident, which is how P1 of the PRD's
 * privacy table is enforced — structurally, rather than by review.
 *
 * An implementation that cannot render (no SDK, no consent, no configured factory) **renders
 * nothing and invokes nothing**. It must never throw, and it must never report a failure it did not
 * actually attempt — an empty slot is the normal quiet outcome, not an error state (N3, G5).
 *
 * @param onFilled a creative was displayed; counts one unit against the session cap.
 * @param onFailed no fill. Never surfaced to the user; feeds `ad_fill_failed` only.
 * @param onClicked the pilot tapped the creative itself, not the upgrade link.
 */
@Composable
expect fun AdView(
  size: AdUnitSize,
  onFilled: () -> Unit,
  onFailed: (reason: String) -> Unit,
  onClicked: () -> Unit,
  modifier: Modifier = Modifier,
)
