package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.common.compose.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionUiState
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_aircraft_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_aircraft_unlimited
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_col_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_compare_header
import wingslog.feature.subscription.viewing.generated.resources.subscription_compare_subhead
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_placeholder
import wingslog.feature.subscription.viewing.generated.resources.subscription_cta_start_trial
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_aircraft
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_attachments
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_backup
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_email
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_export
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_records
import wingslog.feature.subscription.viewing.generated.resources.subscription_feature_sharing
import wingslog.feature.subscription.viewing.generated.resources.subscription_includes_label
import wingslog.feature.subscription.viewing.generated.resources.subscription_includes_value
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage
import wingslog.feature.subscription.viewing.generated.resources.subscription_manage_placeholder
import wingslog.feature.subscription.viewing.generated.resources.subscription_member_since
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_active
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_active_no_date
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_canceled
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_grace
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_label
import wingslog.feature.subscription.viewing.generated.resources.subscription_status_trialing
import wingslog.feature.subscription.viewing.generated.resources.subscription_storage_used
import wingslog.feature.subscription.viewing.generated.resources.subscription_title

/**
 * The subscription page. Non-subscribers see the tier comparison + upgrade CTA; subscribers see
 * their status. Storage used is shown to both. Purchase/Manage are placeholders until billing lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
  navController: NavController,
  viewModel: SubscriptionViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.subscription_title),
          onBackClick = { navController.popBackStack() },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        if (uiState.isPro) {
          SubscriberStatusView(uiState)
        } else {
          ProComparisonView()
        }

        HorizontalDivider()
        InfoRow(
          label = stringResource(Res.string.subscription_storage_used),
          value = uiState.storageBytesUsed.formatFileSize(),
        )
      }
    }
  }
}

@Composable
private fun SubscriberStatusView(state: SubscriptionUiState) {
  InfoRow(stringResource(Res.string.subscription_status_label), statusLine(state))
  state.memberSince?.let {
    InfoRow(stringResource(Res.string.subscription_member_since), it)
  }
  InfoRow(
    stringResource(Res.string.subscription_includes_label),
    stringResource(Res.string.subscription_includes_value),
  )
  Spacer(Modifier.height(Spacing.small))
  OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(Res.string.subscription_manage))
  }
  Text(
    text = stringResource(Res.string.subscription_manage_placeholder),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun statusLine(state: SubscriptionUiState): String = when (state.lifecycle) {
  Subscription.Lifecycle.LIFECYCLE_TRIALING ->
    stringResource(Res.string.subscription_status_trialing)

  Subscription.Lifecycle.LIFECYCLE_CANCELED ->
    if (state.currentPeriodEnd != null) {
      stringResource(Res.string.subscription_status_canceled, state.currentPeriodEnd)
    } else {
      stringResource(Res.string.subscription_status_active_no_date)
    }

  Subscription.Lifecycle.LIFECYCLE_GRACE ->
    stringResource(Res.string.subscription_status_grace)

  else ->
    if (state.willRenew && state.currentPeriodEnd != null) {
      stringResource(Res.string.subscription_status_active, state.currentPeriodEnd)
    } else {
      stringResource(Res.string.subscription_status_active_no_date)
    }
}

@Composable
private fun ProComparisonView() {
  Text(
    text = stringResource(Res.string.subscription_compare_header),
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
  )
  Text(
    text = stringResource(Res.string.subscription_compare_subhead),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(Modifier.height(Spacing.small))

  CompareHeader()
  HorizontalDivider()
  CompareRow(
    stringResource(Res.string.subscription_feature_aircraft),
    Cell.Label(stringResource(Res.string.subscription_aircraft_free)),
    Cell.Label(stringResource(Res.string.subscription_aircraft_unlimited)),
  )
  CompareRow(stringResource(Res.string.subscription_feature_records), Cell.Yes, Cell.Yes)
  CompareRow(stringResource(Res.string.subscription_feature_export), Cell.Yes, Cell.Yes)
  CompareRow(stringResource(Res.string.subscription_feature_backup), Cell.Yes, Cell.Yes)
  CompareRow(stringResource(Res.string.subscription_feature_attachments), Cell.No, Cell.Yes)
  CompareRow(stringResource(Res.string.subscription_feature_email), Cell.No, Cell.Yes)
  CompareRow(stringResource(Res.string.subscription_feature_sharing), Cell.No, Cell.Yes)

  Spacer(Modifier.height(Spacing.medium))
  Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
    Text(stringResource(Res.string.subscription_cta_start_trial))
  }
  Text(
    text = stringResource(Res.string.subscription_cta_placeholder),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private const val CELL_WIDTH_DP = 84

@Composable
private fun CompareHeader() {
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Spacer(Modifier.weight(1f))
    ColumnHeader(stringResource(Res.string.subscription_col_free))
    ColumnHeader(stringResource(Res.string.subscription_col_pro))
  }
}

@Composable
private fun ColumnHeader(text: String) {
  Box(modifier = Modifier.width(CELL_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

private sealed interface Cell {
  data object Yes : Cell
  data object No : Cell
  data class Label(val text: String) : Cell
}

@Composable
private fun CompareRow(label: String, free: Cell, pro: Cell) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    CellContent(free)
    CellContent(pro)
  }
}

@Composable
private fun CellContent(cell: Cell) {
  Box(modifier = Modifier.width(CELL_WIDTH_DP.dp), contentAlignment = Alignment.Center) {
    when (cell) {
      Cell.Yes -> Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )

      Cell.No -> Text(
        text = "—",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      is Cell.Label -> Text(
        text = cell.text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.small),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
  }
}
