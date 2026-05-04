package dev.fanfly.wingslog.feature.settings.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.settings.sync.compose.SyncHeroIllustration
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
  navController: NavController,
  viewModel: SyncSettingsViewModel = koinViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = "Sync & backup",
        onBackClick = { navController.popBackStack() },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      // Hero takes the full width without horizontal padding so the illustration breathes.
      Spacer(Modifier.height(Spacing.medium))
      SyncHeroIllustration(active = state.signedIn && state.cloudSyncEnabled)

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
      ) {
        HeroCaption(state = state)

        ToggleCard {
          SyncToggleRow(
            title = "Cloud sync",
            subtitle = cloudSyncSubtitle(state),
            checked = state.cloudSyncEnabled,
            enabled = state.signedIn,
            onCheckedChange = viewModel::onCloudSyncToggled,
          )
        }

        StatusSection(state = state)

        Text(
          text = "Attachments require an internet connection and a permanent (non-anonymous) account. " +
            "Your aircraft, logs, tasks, technicians and license info sync automatically.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.large))
      }
    }
  }
}

@Composable
private fun HeroCaption(state: SyncSettingsUiState) {
  val (title, body) = when {
    !state.signedIn ->
      "Sign in to enable sync" to
        "You're using the app anonymously. All data stays on this device. Sign in with a permanent account on the previous screen to back things up."

    !state.cloudSyncEnabled ->
      "Sync is paused" to
        "Your work stays on this device. Reinstalling the app or moving to a new phone will not bring this data with you."

    else ->
      "Backed up across your devices" to
        "Changes you make here sync to the cloud and any other device signed into the same account."
  }
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ToggleCard(content: @Composable () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    content()
  }
}

@Composable
private fun SyncToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  val titleColor =
    if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
  val subtitleColor =
    if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(
        horizontal = Spacing.large,
        vertical = Spacing.large
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.tiny),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = titleColor
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = subtitleColor
      )
    }
    Spacer(Modifier.width(Spacing.large))
    Switch(
      checked = checked,
      enabled = enabled,
      onCheckedChange = onCheckedChange
    )
  }
}

@Composable
private fun StatusSection(state: SyncSettingsUiState) {
  // Three mutually exclusive cases — only one ever renders. The point of having all three drawn
  // out explicitly is so future-me can spot when a state is missing UX coverage.
  when {
    state.failureMessage != null -> StatusRow(
      icon = Icons.Default.Warning,
      title = "Sync error",
      body = state.failureMessage,
      tint = MaterialTheme.colorScheme.error,
      container = MaterialTheme.colorScheme.errorContainer,
    )

    state.signedIn && state.cloudSyncEnabled && state.hydration is HydrationState.InProgress -> {
      val h = state.hydration
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.primaryContainer)
          .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Text(
          text = "Restoring ${h.completed} of ${h.total} collections",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        LinearProgressIndicator(
          progress = {
            (h.completed.toFloat() / h.total.toFloat()).coerceIn(
              0f,
              1f
            )
          },
          modifier = Modifier.fillMaxWidth(),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
        )
      }
    }

    !state.signedIn -> StatusRow(
      icon = Icons.Default.Info,
      title = "Anonymous account",
      body = "Sync requires signing in.",
      tint = MaterialTheme.colorScheme.secondary,
      container = MaterialTheme.colorScheme.secondaryContainer,
    )

    !state.cloudSyncEnabled -> StatusRow(
      icon = Icons.Default.CloudOff,
      title = "Cloud sync is off",
      body = "Local edits won't reach the cloud. Re-enable above to resume backups.",
      tint = MaterialTheme.colorScheme.tertiary,
      container = MaterialTheme.colorScheme.tertiaryContainer,
    )

    else -> StatusRow(
      icon = Icons.Default.Info,
      title = "All caught up",
      body = "Recent changes have been pushed and any remote updates are applied.",
      tint = MaterialTheme.colorScheme.primary,
      container = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
  }
}

@Composable
private fun StatusRow(
  icon: ImageVector,
  title: String,
  body: String,
  tint: androidx.compose.ui.graphics.Color,
  container: androidx.compose.ui.graphics.Color,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(container)
      .padding(Spacing.large),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(Spacing.extraLarge)
    )
    Spacer(Modifier.width(Spacing.large))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = tint
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

private fun cloudSyncSubtitle(state: SyncSettingsUiState): String = when {
  !state.signedIn -> "Sign in to enable cloud sync."
  state.cloudSyncEnabled -> "Backed up to your account across devices."
  else -> "Your data stays on this device only."
}
