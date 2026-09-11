package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.common.compose.ProBadge
import dev.fanfly.wingslog.core.ui.common.compose.SectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.SectionLabelTracking
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.theme.toneFor
import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_ends
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_ads
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_attachments
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_backup
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_email
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_export
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_records
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_sharing
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_thing
import wingslog.feature.subscription.viewing.generated.resources.subscription_includes_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_member_since
import wingslog.feature.subscription.viewing.generated.resources.subscription_plan_basic_body
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_amazon
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_mac_app_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_play_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_test_store
import wingslog.feature.subscription.viewing.generated.resources.subscription_platform_web
import wingslog.feature.subscription.viewing.generated.resources.subscription_purchased_on
import wingslog.feature.subscription.viewing.generated.resources.subscription_renews
import wingslog.feature.subscription.viewing.generated.resources.subscription_source
import wingslog.feature.subscription.viewing.generated.resources.subscription_source_promotion
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_active
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_canceled
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_grace
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_trialing
import wingslog.feature.subscription.viewing.generated.resources.subscription_storage_used
import wingslog.feature.subscription.viewing.generated.resources.subscription_thing_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_thing_unlimited
import wingslog.feature.subscription.viewing.generated.resources.subscription_unlocked_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_your_plan

private val StatusTracking = 1.1.sp
private val ProLabelIconSize = 14.dp

/**
 * The "Your plan" card: the tier in large type, and — for a subscriber — the facts they come here
 * to check (is it on, when it renews or ends, where it is billed, what it is holding).
 */
@Composable
internal fun PlanCard(state: SubscriptionUiState) {
  SubscriptionPanel(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(horizontal = Spacing.xLarge, vertical = Spacing.extraLarge),
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
            Text(stringResource(platform.labelRes), style = WingslogTypography.dataSmall)
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
        Text(state.storageBytesUsed.formatFileSize(), style = WingslogTypography.dataSmall)
      }
    }
  }
}

/**
 * What the account has, then what Pro adds. On Pro every row is a check; on Basic the second group
 * shows each feature's own glyph instead, so the list reads as a promise rather than a scorecard.
 */
@Composable
internal fun FeatureSections(state: SubscriptionUiState) {
  GroupedSection(stringResource(Res.string.subscription_includes_header)) {
    GroupedRowGroup(
      rows = listOf(
        {
          FeatureRow(
            icon = Icons.Default.Category,
            label = stringResource(Res.string.subscription_feature_thing),
            included = true,
            value = stringResource(
              if (state.isPro) Res.string.subscription_thing_unlimited
              else Res.string.subscription_thing_free
            ),
          )
        },
        {
          FeatureRow(
            icon = Icons.Default.Description,
            label = stringResource(Res.string.subscription_feature_records),
            included = true,
          )
        },
        {
          FeatureRow(
            icon = Icons.Default.FileDownload,
            label = stringResource(Res.string.subscription_feature_export),
            included = true,
          )
        },
        {
          FeatureRow(
            icon = Icons.Default.CloudSync,
            label = stringResource(Res.string.subscription_feature_backup),
            included = true,
          )
        },
      ),
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    ProSectionLabel(stringResource(Res.string.subscription_unlocked_header))
    GroupedRowGroup(
      rows = buildList {
        add {
          FeatureRow(
            icon = Icons.Default.AttachFile,
            label = stringResource(Res.string.subscription_feature_attachments),
            included = state.isPro,
          )
        }
        add {
          FeatureRow(
            icon = Icons.Default.Group,
            label = stringResource(Res.string.subscription_feature_sharing),
            included = state.isPro,
          )
        }
        add {
          FeatureRow(
            icon = Icons.Default.Mail,
            label = stringResource(Res.string.subscription_feature_email),
            included = state.isPro,
          )
        }
        // Only in a build that actually ships ads (#384) — the list has to describe the build the
        // pilot is holding, not one where this row would be advertising a feature that doesn't exist.
        if (state.isAdsSupported) {
          add {
            FeatureRow(
              icon = Icons.Default.VisibilityOff,
              label = stringResource(Res.string.subscription_feature_ads),
              included = state.isPro,
            )
          }
        }
      },
    )
  }
}

/** The "Unlocked with Pro" marker: the section label in advisory amber with the Pro mark beside it. */
@Composable
private fun ProSectionLabel(text: String) {
  Row(
    modifier = Modifier.padding(horizontal = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall + Spacing.extraSmall / 2),
  ) {
    Icon(
      imageVector = Icons.Default.WorkspacePremium,
      contentDescription = null,
      modifier = Modifier.size(ProLabelIconSize),
      tint = MaterialTheme.colorScheme.tertiary,
    )
    Text(
      text = text.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = SectionLabelTracking,
      color = MaterialTheme.colorScheme.tertiary,
    )
  }
}

@Composable
private fun FeatureRow(
  icon: ImageVector,
  label: String,
  included: Boolean,
  value: String? = null,
) {
  GroupedRow(
    title = label,
    titleStyle = MaterialTheme.typography.bodyLarge,
    leading = {
      Icon(
        imageVector = if (included) Icons.Default.Check else icon,
        contentDescription = null,
        modifier = Modifier.size(Spacing.xLarge + Spacing.extraSmall / 2),
        tint = if (included) MaterialTheme.statusColors.positive.accent
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    trailing = value?.let {
      {
        Text(
          text = it,
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

/** A dot and a word — the same status language the rest of the app uses for operational state. */
@Composable
private fun StatusIndicator(lifecycle: Subscription.Lifecycle) {
  val (labelRes, tier) = when (lifecycle) {
    Subscription.Lifecycle.LIFECYCLE_TRIALING ->
      Res.string.subscription_status_trialing to StatusTier.POSITIVE

    // Still Pro, but ending: amber, because there is a decision to make before the period end.
    Subscription.Lifecycle.LIFECYCLE_CANCELED ->
      Res.string.subscription_status_canceled to StatusTier.CAUTION

    // The store could not take payment. Loudest state on the page — access is about to stop.
    Subscription.Lifecycle.LIFECYCLE_GRACE ->
      Res.string.subscription_status_grace to StatusTier.CRITICAL

    else -> Res.string.subscription_status_active to StatusTier.POSITIVE
  }
  val accent = MaterialTheme.statusColors.toneFor(tier).accent
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.size(Spacing.small)
        .background(accent, CircleShape)
    )
    Spacer(Modifier.width(Spacing.small))
    Text(
      text = stringResource(labelRes).uppercase(),
      style = WingslogTypography.dataSmall,
      fontWeight = FontWeight.Bold,
      letterSpacing = StatusTracking,
      color = accent,
    )
  }
}

@Composable
private fun FactRow(
  label: String,
  value: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Spacing.small),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      letterSpacing = SectionLabelTracking,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, content = value)
  }
}

@Composable
private fun FactIcon(icon: ImageVector) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(Spacing.large),
  )
  Spacer(Modifier.width(Spacing.small))
}

/**
 * The store's display name.
 *
 * Lives here rather than on the enum because [PurchasePlatform] is a `model` type shared with the
 * billing layer, which has no Compose resources — and a store's *name* is a presentation concern in
 * a way its identity is not.
 */
private val PurchasePlatform.labelRes: StringResource
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Res.string.subscription_platform_app_store
    PurchasePlatform.MAC_APP_STORE -> Res.string.subscription_platform_mac_app_store
    PurchasePlatform.PLAY_STORE -> Res.string.subscription_platform_play_store
    PurchasePlatform.AMAZON -> Res.string.subscription_platform_amazon
    PurchasePlatform.WEB -> Res.string.subscription_platform_web
    PurchasePlatform.TEST_STORE -> Res.string.subscription_platform_test_store
  }

/** The glyph for the store that billed the subscription — the mark the pilot will recognise there. */
private val PurchasePlatform.icon: ImageVector
  get() = when (this) {
    PurchasePlatform.APP_STORE -> Icons.Default.PhoneIphone
    PurchasePlatform.MAC_APP_STORE -> Icons.Default.Laptop
    PurchasePlatform.PLAY_STORE -> Icons.Default.Android
    PurchasePlatform.AMAZON -> Icons.Default.ShoppingBag
    PurchasePlatform.WEB -> Icons.Default.Language
    PurchasePlatform.TEST_STORE -> Icons.Default.Science
  }
