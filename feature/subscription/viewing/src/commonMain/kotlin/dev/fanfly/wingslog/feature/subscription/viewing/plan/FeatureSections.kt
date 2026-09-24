package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.grouped.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.subscription.viewing.SubscriptionUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_ads
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_attachments
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_backup
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_email
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_export
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_records
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_sharing
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_thing
import wingslog.feature.subscription.viewing.generated.resources.subscription_includes_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_thing_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_thing_unlimited
import wingslog.feature.subscription.viewing.generated.resources.subscription_unlocked_header

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
