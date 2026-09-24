package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.badge.ProBadge
import dev.fanfly.wingslog.core.ui.list.SectionLabel
import dev.fanfly.wingslog.core.ui.text.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.subscription.viewing.SubscriptionPanel
import dev.fanfly.wingslog.feature.subscription.viewing.SubscriptionUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_ends
import wingslog.feature.subscription.viewing.generated.resources.subscription_member_since
import wingslog.feature.subscription.viewing.generated.resources.subscription_plan_basic_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_purchased_on
import wingslog.feature.subscription.viewing.generated.resources.subscription_renews
import wingslog.feature.subscription.viewing.generated.resources.subscription_source
import wingslog.feature.subscription.viewing.generated.resources.subscription_source_promotion
import wingslog.feature.subscription.viewing.generated.resources.subscription_storage_used
import wingslog.feature.subscription.viewing.generated.resources.subscription_your_plan

internal val StatusTracking = 1.1.sp

/**
 * The "Your plan" card: the tier in large type, and — for a subscriber — the facts they come here
 * to check (is it on, when it renews or ends, where it is billed, what it is holding).
 */
@Composable
internal fun PlanCard(state: SubscriptionUiState) {
  SubscriptionPanel(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(
        horizontal = Spacing.xLarge,
        vertical = Spacing.extraLarge
      ),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      SectionLabel(stringResource(Res.string.subscription_your_plan))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Text(
          text = stringResource(
            if (state.isPro) Res.string.subscription_col_pro else Res.string.subscription_col_free
          ),
          style = MaterialTheme.typography.headlineLarge,
        )
        if (state.isPro) ProBadge()
      }
      if (state.isPro) {
        StatusIndicator(state.lifecycle)
        Spacer(Modifier.size(Spacing.extraSmall))
        state.memberSince?.let {
          FactRow(label = stringResource(Res.string.subscription_member_since)) {
            Text(it, style = WingslogTypography.dataSmall)
          }
        }
        state.currentPeriodEnd?.let {
          FactRow(
            // "Renews" would be a lie once the subscription is set to lapse, and the date is the
            // one thing a pilot on their way out is here to check.
            label = stringResource(
              if (state.willRenew) Res.string.subscription_renews else Res.string.subscription_ends
            ),
          ) {
            Text(it, style = WingslogTypography.dataSmall)
          }
        }
        // A granted subscription names its source instead of a store (#750). "Purchased on" would
        // be false — nothing was purchased — and leaving the row out entirely left a comped member
        // with no account of where their Pro came from.
        if (state.isComped) {
          FactRow(label = stringResource(Res.string.subscription_source)) {
            FactIcon(Icons.Default.Redeem)
            Text(
              stringResource(Res.string.subscription_source_promotion),
              style = WingslogTypography.dataSmall
            )
          }
        }
        // Omitted when there is no store to name (a comp, or an unrecognised platform). Sourced
        // from the synced entitlement, so it names the store that actually billed even when the
        // pilot is reading this on a different platform.
        state.purchasePlatform?.let { platform ->
          FactRow(label = stringResource(Res.string.subscription_purchased_on)) {
            FactIcon(platform.icon)
            Text(
              stringResource(platform.labelRes),
              style = WingslogTypography.dataSmall
            )
          }
        }
      } else {
        Text(
          text = stringResource(Res.string.subscription_plan_basic_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.extraSmall))
      }
      FactRow(label = stringResource(Res.string.subscription_storage_used)) {
        Text(
          state.storageBytesUsed.formatFileSize(),
          style = WingslogTypography.dataSmall
        )
      }
    }
  }
}
