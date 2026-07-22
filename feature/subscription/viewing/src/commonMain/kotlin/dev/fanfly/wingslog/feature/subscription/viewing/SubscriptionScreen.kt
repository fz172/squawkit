package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.SubscriptionViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_coming_soon
import wingslog.feature.subscription.viewing.generated.resources.subscription_tier_free
import wingslog.feature.subscription.viewing.generated.resources.subscription_tier_pro
import wingslog.feature.subscription.viewing.generated.resources.subscription_title

/**
 * The subscription status / comparison page. Placeholder in P4a — P4c fills in the subscriber status
 * view, the non-subscriber comparison table, and the storage-used row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
  navController: NavController,
  viewModel: SubscriptionViewModel = koinViewModel(),
) {
  val status by viewModel.status.collectAsStateWithLifecycle()

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
      contentAlignment = Alignment.Center,
    ) {
      val tierLabel = stringResource(
        if (status == Subscription.Status.STATUS_PRO) Res.string.subscription_tier_pro
        else Res.string.subscription_tier_free
      )
      Text(
        text = stringResource(Res.string.subscription_coming_soon, tierLabel),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
