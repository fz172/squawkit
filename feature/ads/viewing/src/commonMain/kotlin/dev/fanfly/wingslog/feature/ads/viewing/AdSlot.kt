package dev.fanfly.wingslog.feature.ads.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.fanfly.wingslog.core.analytics.LocalAnalytics
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.datamanager.AdsManager
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import dev.fanfly.wingslog.feature.ads.model.AdLayoutTier
import dev.fanfly.wingslog.feature.ads.model.AdSlotFormat
import dev.fanfly.wingslog.feature.ads.model.AdSlotKey
import dev.fanfly.wingslog.feature.ads.model.AdSurface
import dev.fanfly.wingslog.feature.ads.model.AdUnitSize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.ads.sharedassets.generated.resources.Res
import wingslog.feature.ads.sharedassets.generated.resources.ads_a11y_advertisement
import wingslog.feature.ads.sharedassets.generated.resources.ads_sponsored_label

/** `LayoutTier` lives in a Compose module; the placement core deliberately does not depend on it. */
internal fun LayoutTier.toAdLayoutTier(): AdLayoutTier = when (this) {
  LayoutTier.COMPACT -> AdLayoutTier.COMPACT
  LayoutTier.MEDIUM -> AdLayoutTier.MEDIUM
  // §7.1 treats these identically — both are wide enough for two thin units side by side.
  LayoutTier.EXPANDED, LayoutTier.LARGE -> AdLayoutTier.WIDE
}

/**
 * One ad slot, rendered as a card that sits *in* the record list rather than over it.
 *
 * Owns everything that makes an ad card an ad card — the "Sponsored" label (once per slot, not per
 * unit), the band layout, collapsing to zero height, and every analytics event. [AdView] owns
 * nothing but the creative.
 *
 * **Budget is claimed once per slot and cached for the life of the composition** (N8). Scrolling
 * past the same ad twice therefore neither re-requests nor double-counts — the reservation is keyed
 * on the slot, not on how many times it happens to be composed.
 *
 * It is claimed when the ad is *requested* rather than when it fills. This is not a limit on how
 * many ads can be on screen at once — a wide band shows two units, and a session may show five.
 * It matters only at the boundary: with one unit of budget left and two slots composing in the same
 * frame, counting on fill would let both read "1 remaining", both request, and both display — six
 * units against a cap of five. Claiming up front means the first slot takes the last unit and the
 * second is granted nothing and renders nothing.
 *
 * A unit that then fails to fill is released, since only filled units count against the cap.
 *
 * When no unit is granted the composable emits **nothing at all** — not an empty box, not a
 * placeholder. A slot the pilot cannot see must not occupy a pixel or shift a record under their
 * finger (G5).
 */
@Composable
fun AdSlot(
  surface: AdSurface,
  slotIndex: Int,
  modifier: Modifier = Modifier,
) {
  val adsManager: AdsManager = koinInject()
  val adConsentManager: AdConsentManager = koinInject()
  val appCapability: AppCapability = koinInject()
  val analytics = LocalAnalytics.current
  val adTier = LocalLayoutTier.current.toAdLayoutTier()

  val key = remember(surface, slotIndex) { AdSlotKey(surface, slotIndex) }

  // Consent is resolved before anything else touches the session budget or requests a creative
  // (design §8's ordering: showsAds() → ensureConsent() → MobileAds.initialize() → first request).
  // Nothing is claimed or shown while this is unresolved — same zero-height contract as "no unit
  // granted" below — and a slot the CMP says must not request ads at all never reaches `reserve()`.
  var consent by remember(key) { mutableStateOf<AdConsentState?>(null) }
  LaunchedEffect(key) { consent = adConsentManager.ensureConsent() }
  if (consent == null || consent == AdConsentState.DENIED) return

  // The counter, not this composable, owns the grant: a slot disposed by scrolling or a tab switch
  // comes back as a fresh composable, and reserve() is idempotent per key so it gets the same answer
  // rather than paying again — or, at the cap, being refused an ad the pilot has already seen.
  // reserve() is the only authority on what this slot may render, and it is asked unconditionally.
  // Checking remaining headroom first was the bug: at the cap it answered "nothing available" and
  // the slot returned before reserve() could hand back the grant it already held, so an ad the pilot
  // had already seen vanished when they scrolled back to it.
  val granted = remember(key) {
    adsManager.reserve(key, AdSlotFormat.desiredUnits(adTier))
  }
  val format = AdSlotFormat.forGrant(granted) ?: return

  var filled by remember(key) { mutableStateOf(0) }
  var failed by remember(key) { mutableStateOf(0) }
  // Every unit came back empty: collapse rather than show a labelled box around nothing.
  if (failed >= granted) return

  val advertisement = stringResource(Res.string.ads_a11y_advertisement)

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      // Neutral only. The amber/red status palette means "your aircraft needs attention" (G8).
      containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        // Both axes trimmed from Spacing.medium: the creative is a fixed size regardless of how
        // big this card is, so less inset all around means less dead space around it.
        .padding(horizontal = Spacing.small, vertical = Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = stringResource(Res.string.ads_sponsored_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          // One focus group announced as "Advertisement" (N7).
          .clearAndSetSemantics { contentDescription = advertisement },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(granted) { unit ->
          AdView(
            size = AdUnitSize.BANNER,
            surface = surface,
            // Developer builds request test inventory: real impressions from development are
            // invalid traffic, which AdMob suspends accounts for.
            useTestAds = appCapability.isDeveloperOptionsSupported,
            onFilled = {
              filled++
              val position = unitPosition(format, unit)
              analytics.logEvent(
                "ad_slot_filled",
                mapOf(
                  "surface" to surface.analyticsName,
                  "slot_index" to slotIndex.toString(),
                  "unit_position" to position,
                ),
              )
              // Once per slot per session. A slot re-rendered because the pilot scrolled back is the
              // same impression; counting it again would inflate the number §12 reads as revenue.
              if (adsManager.markImpressionLogged(key)) {
                analytics.logEvent(
                  "ad_impression",
                  mapOf(
                    "surface" to surface.analyticsName,
                    "slot_index" to slotIndex.toString(),
                    "unit_position" to position,
                  ),
                )
              }
            },
            onFailed = { reason ->
              failed++
              // Give the unit back: the PRD counts filled units only, so a run of no-fills must not
              // quietly eat the session allowance.
              adsManager.release(key, 1)
              analytics.logEvent(
                "ad_fill_failed",
                mapOf("surface" to surface.analyticsName, "reason" to reason),
              )
            },
            onClicked = {
              analytics.logEvent(
                "ad_click",
                mapOf(
                  "surface" to surface.analyticsName,
                  "slot_index" to slotIndex.toString(),
                  "unit_position" to unitPosition(format, unit),
                ),
              )
            },
          )
        }
      }
    }
  }
}

/** The `unit_position` analytics param: `single`, or `left`/`right` within a two-up band. */
private fun unitPosition(format: AdSlotFormat, unitIndex: Int): String = when {
  format == AdSlotFormat.SINGLE -> "single"
  unitIndex == 0 -> "left"
  else -> "right"
}
