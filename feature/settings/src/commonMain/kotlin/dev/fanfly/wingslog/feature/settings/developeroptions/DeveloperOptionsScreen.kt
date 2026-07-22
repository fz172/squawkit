package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.developer_options
import wingslog.feature.settings.generated.resources.developer_options_subtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
  navController: NavController,
  viewModel: DeveloperOptionsViewModel = koinViewModel(),
  dogfoodContent: @Composable () -> Unit = {},
) {
  val flags by viewModel.flags.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.developer_options),
          onBackClick = { navController.popBackStack() },
        )
      }
    }
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
          .padding(Spacing.screenPadding),
      ) {
        Text(
          text = stringResource(Res.string.developer_options_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.large))

        HorizontalDivider()

        ForceSubscriptionRow(
          current = flags.forceSubscriptionStatus,
          onSelect = viewModel::setForceSubscriptionStatus,
        )
        HorizontalDivider()

        dogfoodContent()
      }
    }
  }
}

/**
 * Developer-only override of the effective subscription tier. Off = use the real entitlement; Free
 * or Pro force that tier locally (honored only in developer builds). Labels are hardcoded — this
 * screen never ships to real users.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForceSubscriptionRow(
  current: Subscription.Status?,
  onSelect: (Subscription.Status?) -> Unit,
) {
  val options: List<Pair<String, Subscription.Status?>> = listOf(
    "Off" to null,
    "Free" to Subscription.Status.STATUS_FREE,
    "Pro" to Subscription.Status.STATUS_PRO,
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
          text = "Force subscription status",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
        )
        Text(
          text = "Make this account behave as the chosen tier, ignoring the real entitlement.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.height(Spacing.medium))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      options.forEachIndexed { index, (label, status) ->
        SegmentedButton(
          selected = current == status,
          onClick = { onSelect(status) },
          shape = SegmentedButtonDefaults.itemShape(index, options.size),
        ) {
          Text(label)
        }
      }
    }
  }
}
