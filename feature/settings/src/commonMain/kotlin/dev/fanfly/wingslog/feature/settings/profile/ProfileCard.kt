package dev.fanfly.wingslog.feature.settings.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.badge.ProBadge
import dev.fanfly.wingslog.core.ui.grouped.GroupedChevron
import dev.fanfly.wingslog.core.ui.grouped.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.grouped.GroupedRow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import dev.fanfly.wingslog.feature.settings.PlanRow
import dev.fanfly.wingslog.feature.settings.SettingsUiState
import dev.fanfly.wingslog.feature.settings.row.SettingsRowGroup
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.settings_plan_basic
import wingslog.feature.settings.generated.resources.settings_plan_pro
import wingslog.feature.settings.generated.resources.settings_plan_pro_ends
import wingslog.feature.settings.generated.resources.settings_plan_pro_renews
import wingslog.feature.settings.generated.resources.settings_profile_guest
import wingslog.feature.settings.generated.resources.settings_subscription
import wingslog.feature.settings.generated.resources.Res as SettingsRes

private val ProfileAvatarSize = 56.dp

/**
 * Who is signed in and what they are paying for, in one card at the top: the avatar row opens the
 * profile editor (the chevron says so; the subtitle is just the account), the plan row opens
 * Subscription. Dividers inset to the row padding rather than
 * the chip column — the avatar is not a chip, so a chip-aligned rule would float.
 */
@Composable
internal fun ProfileCard(
  user: SettingsUiState,
  onOpenProfile: () -> Unit,
  onOpenSubscription: () -> Unit,
) {
  SettingsRowGroup(
    dividerStartInset = Spacing.xLarge,
    rows = listOf(
      {
        GroupedRow(
          title = user.displayName
            ?: stringResource(SettingsRes.string.settings_profile_guest),
          titleStyle = MaterialTheme.typography.titleLarge,
          subtitle = user.email,
          onClick = onOpenProfile,
          leading = {
            AvatarIcon(
              displayName = user.displayName,
              photoUri = user.photoUrl,
              size = ProfileAvatarSize,
              textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
          },
          trailing = { GroupedChevron() },
        )
      },
      {
        val plan = user.plan
        GroupedRow(
          title = stringResource(SettingsRes.string.settings_subscription),
          subtitle = when (plan) {
            null -> null
            PlanRow.Basic -> stringResource(SettingsRes.string.settings_plan_basic)
            is PlanRow.Pro -> when {
              plan.periodEnd == null -> stringResource(SettingsRes.string.settings_plan_pro)
              plan.willRenew ->
                stringResource(
                  SettingsRes.string.settings_plan_pro_renews,
                  plan.periodEnd
                )

              else -> stringResource(
                SettingsRes.string.settings_plan_pro_ends,
                plan.periodEnd
              )
            }
          },
          onClick = onOpenSubscription,
          leading = {
            GroupedLeadingIconChip(
              icon = Icons.Default.WorkspacePremium,
              contentDescription = null,
            )
          },
          trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (plan is PlanRow.Pro) {
                ProBadge()
                Spacer(Modifier.width(Spacing.small))
              }
              GroupedChevron()
            }
          },
        )
      },
    ),
  )
}
