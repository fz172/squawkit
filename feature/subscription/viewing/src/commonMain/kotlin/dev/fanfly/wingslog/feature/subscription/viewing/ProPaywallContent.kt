package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_activating
import wingslog.feature.subscription.viewing.generated.resources.subscription_activation_recheck
import wingslog.feature.subscription.viewing.generated.resources.subscription_activation_stalled
import wingslog.feature.subscription.viewing.generated.resources.subscription_activation_stalled_promo
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_purchase_on_mobile
import wingslog.feature.subscription.viewing.generated.resources.subscription_sign_in_to_subscribe

/**
 * The pre-purchase page: what the account has, what Pro adds, and one way to get it.
 *
 * The argument is scale, not features — Basic is already a complete logbook, so the lists lead
 * with what is included and only then what Pro unlocks. Nothing here quotes a price; the store's
 * paywall does that, and saying so up front is what keeps the CTA honest.
 */
@Composable
internal fun ProPaywallContent(
  state: SubscriptionUiState,
  onSubscribe: () -> Unit,
  onRedeemPromo: () -> Unit,
  onActivationRecheck: () -> Unit,
) {
  PlanCard(state)
  FeatureSections(state)

  SubscribeButton(
    onClick = onSubscribe,
    // Disabled while activating so a pilot who has just paid can't start a second purchase in the
    // window before their entitlement syncs, and for a guest, who has no durable account to attach
    // a subscription to.
    enabled = state.isPurchaseSupported && !state.isActivating && !state.isGuest,
  )

  // A stalled activation outranks everything: it is the only state here carrying news the pilot
  // does not already have, and the one they are most anxious about.
  if (state.isActivationStalled) {
    StalledActivationNotice(
      isPromo = state.promoActivationTerm != null,
      onRecheck = onActivationRecheck,
    )
  } else {
    // Most actionable first: a guest can fix their case, and until they do nothing else about the
    // button matters. A promo activation outranks the purchase line because it names what the pilot
    // just did; the default line sets expectations for the store sheet that is about to open.
    SubscriptionCaption(
      text = when {
        state.isGuest -> stringResource(Res.string.subscription_sign_in_to_subscribe)
        state.promoActivationTerm != null ->
          stringResource(state.promoActivationTerm.activatingRes)

        state.isActivating -> stringResource(Res.string.subscription_activating)
        // Web: purchasing is mobile-only, but a subscription bought there unlocks Pro here too.
        !state.isPurchaseSupported -> stringResource(Res.string.subscription_purchase_on_mobile)
        else -> stringResource(Res.string.subscription_cta_caption)
      },
      textAlign = TextAlign.Center,
    )
  }

  // A second way in, not a second pitch — quiet enough that it never competes with the CTA above,
  // and present even where purchasing is not (web can redeem a code perfectly well). Hidden while a
  // redemption is already landing, so the page cannot invite a second one over the first.
  if (state.canRedeemPromo && state.promoActivationTerm == null) {
    PromoCodeEntryButton(onRedeemPromo)
  }
}

/**
 * Shown when an activation has outlived the point where "Activating…" is still informative.
 *
 * Deliberately not an error. Nothing has failed from the pilot's side — they paid, or they redeemed
 * a code, and both succeeded; what is late is an entitlement arriving. So the copy leads with what
 * is safe rather than with what went wrong, and the caution tone is the page's existing "there is a
 * decision here" colour, not its critical one.
 *
 * The action re-asks rather than re-buys: there is no circumstance in which the right response to a
 * slow entitlement is a second purchase, so no path from here leads back to the store.
 */
@Composable
private fun StalledActivationNotice(isPromo: Boolean, onRecheck: () -> Unit) {
  val caution = MaterialTheme.statusColors.toneFor(StatusTier.CAUTION).accent
  SubscriptionPanel(
    modifier = Modifier.fillMaxWidth(),
    borderColor = caution.copy(alpha = NOTICE_BORDER_TINT),
    containerColor = caution.copy(alpha = NOTICE_SURFACE_TINT),
  ) {
    Column(modifier = Modifier.padding(Spacing.large)) {
      Text(
        text = stringResource(
          if (isPromo) {
            Res.string.subscription_activation_stalled_promo
          } else {
            Res.string.subscription_activation_stalled
          },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(
        onClick = onRecheck,
        modifier = Modifier.align(Alignment.End),
      ) {
        Text(stringResource(Res.string.subscription_activation_recheck))
      }
    }
  }
}
