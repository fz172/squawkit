package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.developer_options
import wingslog.feature.settings.generated.resources.developer_options_subtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(
  navController: NavController,
  viewModel: DeveloperOptionsViewModel = koinViewModel(),
) {
  val flags by viewModel.flags.collectAsStateWithLifecycle()

  // Sections contributed by other features, resolved rather than passed in, so neither this module
  // nor feature:shell has to depend on whoever owns them. Sorted here rather than at registration
  // so ordering does not depend on Koin module order. Remembered because getAll() walks the
  // definition registry — that is startup-cheap but not per-recomposition cheap.
  val koin = getKoin()
  val extras = remember {
    koin.getAll<DeveloperOptionsExtra>()
      .sortedBy { it.order }
  }

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
          .padding(Spacing.screenPadding)
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = stringResource(Res.string.developer_options_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.large))

        HorizontalDivider()

        SubscriptionDeveloperSettings(
          forceStatus = flags.forceSubscriptionStatus,
          onSelectForceStatus = viewModel::setForceSubscriptionStatus,
        )
        HorizontalDivider()

        DisplayAdsDeveloperSettings(
          forceAds = flags.forceAds,
          onToggleForceAds = viewModel::setForceAds,
          adConsentTestDeviceHashedId = flags.adConsentTestDeviceHashedId,
          onAdConsentTestDeviceHashedIdChange = viewModel::setAdConsentTestDeviceHashedId,
          onResetAdConsent = viewModel::resetAdConsent,
        )
        HorizontalDivider()

        extras
          .filter { it.isAvailable() }
          .forEach { extra ->
            extra.Content(onNavigate = { route -> navController.navigate(route) })
            HorizontalDivider()
          }
      }
    }
  }
}
