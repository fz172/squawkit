package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.developer_options_force_free
import wingslog.feature.settings.generated.resources.developer_options_force_off
import wingslog.feature.settings.generated.resources.developer_options_force_pro
import wingslog.feature.settings.generated.resources.developer_options_force_subscription_subtitle
import wingslog.feature.settings.generated.resources.developer_options_force_subscription_title

/**
 * Developer-only override of the effective subscription tier. Off = use the real entitlement; Free
 * or Pro force that tier locally (honored only in developer builds).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDeveloperSettings(
  forceStatus: Subscription.Status?,
  onSelectForceStatus: (Subscription.Status?) -> Unit,
) {
  val options: List<Pair<String, Subscription.Status?>> = listOf(
    stringResource(Res.string.developer_options_force_off) to null,
    stringResource(Res.string.developer_options_force_free) to Subscription.Status.STATUS_FREE,
    stringResource(Res.string.developer_options_force_pro) to Subscription.Status.STATUS_PRO,
  )
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.Star,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = Spacing.medium),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(Res.string.developer_options_force_subscription_title),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = stringResource(Res.string.developer_options_force_subscription_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.height(Spacing.medium))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, (label, status) ->
        SegmentedButton(
          selected = forceStatus == status,
          onClick = { onSelectForceStatus(status) },
          shape = SegmentedButtonDefaults.itemShape(index, options.size),
        ) {
          Text(label)
        }
      }
    }
  }
}
