package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage_link_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage_store_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_body_web
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_caption
import wingslog.feature.subscription.viewing.generated.resources.subscription_managed_elsewhere_title

/**
 * The post-purchase page: a receipt, not a pitch.
 *
 * Everything the subscriber might come here to check is in the plan card at the top — is it on,
 * since when, what happens next, how much storage it is holding, and which store to go back to.
 * The feature lists below are a reminder of what the money buys, not another attempt to sell it.
 */
@Composable
internal fun ProMembershipContent(
  state: SubscriptionUiState,
  onManage: () -> Unit,
) {
  PlanCard(state)
  FeatureSections(state)

  Spacer(Modifier.height(Spacing.small))
  val managementUrl = state.managementUrl
  when {
    // Granted, not bought: nothing to cancel, nowhere to send anyone, so no affordance at all. Even
    // the "managed on another platform" notice would be wrong — it tells the pilot to open the app
    // on the device they purchased with, and there was no purchase.
    state.isComped -> Unit
    // This build's own store sold it: the Customer Center is richer than any link — it can change
    // plan, apply a promo and handle a refund request in-app.
    state.canManage -> ManageAction(onManage)
    // No Customer Center here, but we know where the plan lives — either exactly, from the provider,
    // or at least which store's subscriptions page to open (#363, #361). Either beats telling the
    // pilot to go find another device.
    managementUrl != null -> ManageElsewhereLink(
      managementUrl,
      state.isManagementUrlDerived
    )

    else -> ManagedElsewhere(isPurchaseSupported = state.isPurchaseSupported)
  }

}

@Composable
private fun ManageAction(onManage: () -> Unit) {
  ManageButton(
    caption = stringResource(Res.string.subscription_manage_caption),
    onClick = onManage,
  )
}

/**
 * Manage a subscription this build's store cannot reach, by opening the store's own page.
 *
 * The web app's only route to managing anything: `purchases-kmp` publishes no Kotlin/JS variant, so
 * there is no Customer Center and no `CustomerInfo.managementUrl` to ask for locally — the URL has
 * to be carried on the synced entitlement instead (#363). Equally the right answer on a native build
 * showing a subscription bought on some other store.
 *
 * The caption names no store because the card above already does, in a row sourced from the same
 * entitlement — repeating it under the button would be the third time on one screen. It does say
 * whether the destination is *this* subscription or merely the store's subscriptions list, since
 * that is the one thing the pilot cannot tell before tapping.
 *
 * @param isDerived the URL is our own per-store page, not the provider's deep link.
 */
@Composable
private fun ManageElsewhereLink(url: String, isDerived: Boolean) {
  val uriHandler = LocalUriHandler.current
  ManageButton(
    caption = stringResource(
      if (isDerived) {
        Res.string.subscription_manage_store_caption
      } else {
        Res.string.subscription_manage_link_caption
      },
    ),
    onClick = { uriHandler.openUri(url) },
  )
}

/** The page's one "leave for the store" control, whatever is on the other end of it. */
@Composable
private fun ManageButton(caption: String, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    modifier = Modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight),
  ) {
    Text(
      text = stringResource(Res.string.subscription_manage).uppercase(),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.width(Spacing.small))
    // Leaving the app: the Customer Center is the store's surface, not ours.
    Icon(
      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
      contentDescription = null,
      modifier = Modifier.size(Spacing.xLarge),
    )
  }
  SubscriptionCaption(
    text = caption,
    textAlign = TextAlign.Center,
  )
}

/**
 * The subscription is real and Pro is unlocked here, but this device's store did not sell it.
 *
 * Shown as an explanation *above* a visibly inert button rather than by hiding the control: a
 * subscriber looking for the cancel button will keep hunting for it, so the page has to say where it
 * went. The reassurance at the bottom is the other half — "you can't manage it here" must not read
 * as "your subscription doesn't work here".
 *
 * @param isPurchaseSupported false on web, which has no store to be a *different* store from.
 */
@Composable
private fun ManagedElsewhere(isPurchaseSupported: Boolean) {
  val caution = MaterialTheme.statusColors.toneFor(StatusTier.CAUTION).accent
  SubscriptionPanel(
    modifier = Modifier.fillMaxWidth(),
    borderColor = caution.copy(alpha = NOTICE_BORDER_TINT),
    containerColor = caution.copy(alpha = NOTICE_SURFACE_TINT),
  ) {
    Row(
      modifier = Modifier.padding(Spacing.large),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = caution,
        modifier = Modifier.size(Spacing.xLarge),
      )
      Column {
        Text(
          text = stringResource(Res.string.subscription_managed_elsewhere_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = if (isPurchaseSupported) {
            stringResource(Res.string.subscription_managed_elsewhere_body)
          } else {
            stringResource(Res.string.subscription_managed_elsewhere_body_web)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = Spacing.extraSmall),
        )
      }
    }
  }

  Spacer(Modifier.height(Spacing.medium))
  SubscriptionCaption(
    text = stringResource(Res.string.subscription_managed_elsewhere_caption),
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(top = Spacing.medium),
  )
}
