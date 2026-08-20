package dev.fanfly.wingslog.feature.notifications.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_coming_soon
import wingslog.feature.notifications.settings.generated.resources.notification_settings_title

/**
 * Scaffolded in P1.8 so `Screen.Notifications` has a real destination the moment the settings row
 * can navigate to it — a route with no registered composable is a crash, not a missing feature. The
 * full screen (toggle groups, permission/precondition banners, the AOG confirm) is P1.9; this is
 * deliberately a stand-in, not a first draft to iterate on in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
  navController: NavController,
) {
  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.notification_settings_title),
          onBackClick = { navController.popBackStack() },
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stringResource(Res.string.notification_settings_coming_soon),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .padding(Spacing.screenPadding),
      )
    }
  }
}
