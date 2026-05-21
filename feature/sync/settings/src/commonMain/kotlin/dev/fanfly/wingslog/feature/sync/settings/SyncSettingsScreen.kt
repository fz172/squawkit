package dev.fanfly.wingslog.feature.sync.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.datamanager.ExportDeliveryEmailSource
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncFailure
import dev.fanfly.wingslog.feature.sync.settings.compose.SyncHeroIllustration
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.sync.settings.generated.resources.Res
import wingslog.feature.sync.settings.generated.resources.setting_item_sync
import wingslog.feature.sync.settings.generated.resources.setting_item_sync_on_cellular
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_description
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_disabled_body
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_field_label
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_helper
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_resolved_auth
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_resolved_explicit
import wingslog.feature.sync.settings.generated.resources.sync_export_delivery_title
import wingslog.feature.sync.settings.generated.resources.sync_attachments_disclaimer
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_active
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_paused
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_signin
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_active
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_paused
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_signin
import wingslog.feature.sync.settings.generated.resources.sync_status_anonymous_body
import wingslog.feature.sync.settings.generated.resources.sync_status_anonymous_title
import wingslog.feature.sync.settings.generated.resources.sync_status_auth_expired_body
import wingslog.feature.sync.settings.generated.resources.sync_status_error_title
import wingslog.feature.sync.settings.generated.resources.sync_status_hydration_error_body
import wingslog.feature.sync.settings.generated.resources.sync_status_off_body
import wingslog.feature.sync.settings.generated.resources.sync_status_off_title
import wingslog.feature.sync.settings.generated.resources.sync_status_push_error_body
import wingslog.feature.sync.settings.generated.resources.sync_status_restoring
import wingslog.feature.sync.settings.generated.resources.sync_status_synced_body
import wingslog.feature.sync.settings.generated.resources.sync_status_synced_title
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_active
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_cellular_disabled
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_cellular_enabled
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_off
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_signin
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes

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
        title = stringResource(SyncRes.string.feature_name_backup_and_sync),
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
            title = stringResource(Res.string.setting_item_sync),
            subtitle = when {
              !state.signedIn -> stringResource(Res.string.sync_subtitle_signin)
              state.cloudSyncEnabled -> stringResource(Res.string.sync_subtitle_active)
              else -> stringResource(Res.string.sync_subtitle_off)
            },
            checked = state.cloudSyncEnabled,
            enabled = state.signedIn,
            onCheckedChange = viewModel::onCloudSyncToggled,
          )
          if (state.attachmentEnabled) {
            HorizontalDivider(
              color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.4f
              )
            )
            SyncToggleRow(
              title = stringResource(Res.string.setting_item_sync_on_cellular),
              subtitle = if (state.allowUploadOnCellular)
                stringResource(Res.string.sync_subtitle_cellular_enabled)
              else
                stringResource(Res.string.sync_subtitle_cellular_disabled),
              checked = state.allowUploadOnCellular,
              enabled = state.signedIn && state.cloudSyncEnabled,
              onCheckedChange = viewModel::onAllowUploadOnCellularToggled,
            )
          }
        }

        ExportDeliveryCard(
          state = state,
          onEmailChanged = viewModel::onExportDestinationEmailChanged,
        )

        StatusSection(state = state)

        Text(
          text = stringResource(Res.string.sync_attachments_disclaimer),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.large))
      }
    }
  }
}

@Composable
private fun ExportDeliveryCard(
  state: SyncSettingsUiState,
  onEmailChanged: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = stringResource(Res.string.sync_export_delivery_title),
      style = MaterialTheme.typography.titleMedium,
    )
    Text(
      text = if (state.signedIn) {
        stringResource(Res.string.sync_export_delivery_description)
      } else {
        stringResource(Res.string.sync_export_delivery_disabled_body)
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
      value = state.exportDestinationEmail,
      onValueChange = onEmailChanged,
      enabled = state.signedIn,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(Res.string.sync_export_delivery_field_label)) },
      supportingText = {
        Text(
          text = when (val resolved = state.resolvedExportDelivery) {
            null -> stringResource(Res.string.sync_export_delivery_helper)
            else -> when (resolved.source) {
              ExportDeliveryEmailSource.EXPLICIT ->
                stringResource(Res.string.sync_export_delivery_resolved_explicit, resolved.destinationEmail)
              ExportDeliveryEmailSource.AUTH_FALLBACK ->
                stringResource(Res.string.sync_export_delivery_resolved_auth, resolved.destinationEmail)
            }
          }
        )
      },
    )
  }
}

@Composable
private fun HeroCaption(state: SyncSettingsUiState) {
  val (title, body) = when {
    !state.signedIn ->
      stringResource(Res.string.sync_hero_title_signin) to
        stringResource(Res.string.sync_hero_body_signin)

    !state.cloudSyncEnabled ->
      stringResource(Res.string.sync_hero_title_paused) to
        stringResource(Res.string.sync_hero_body_paused)

    else ->
      stringResource(Res.string.sync_hero_title_active) to
        stringResource(Res.string.sync_hero_body_active)
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
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
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
    state.failure != null -> StatusRow(
      icon = Icons.Default.Warning,
      title = stringResource(Res.string.sync_status_error_title),
      body = state.failure.displayText(),
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
          text = stringResource(
            Res.string.sync_status_restoring,
            h.completed,
            h.total
          ),
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

    !state.signedIn -> {
      StatusRow(
        icon = Icons.Default.Info,
        title = stringResource(Res.string.sync_status_anonymous_title),
        body = stringResource(Res.string.sync_status_anonymous_body),
        tint = MaterialTheme.colorScheme.secondary,
        container = MaterialTheme.colorScheme.secondaryContainer,
      )
    }

    !state.cloudSyncEnabled -> StatusRow(
      icon = Icons.Default.CloudOff,
      title = stringResource(Res.string.sync_status_off_title),
      body = stringResource(Res.string.sync_status_off_body),
      tint = MaterialTheme.colorScheme.tertiary,
      container = MaterialTheme.colorScheme.tertiaryContainer,
    )

    else -> StatusRow(
      icon = Icons.Default.Info,
      title = stringResource(Res.string.sync_status_synced_title),
      body = stringResource(Res.string.sync_status_synced_body),
      tint = MaterialTheme.colorScheme.primary,
      container = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
  }
}

@Composable
private fun SyncFailure.displayText(): String = when (this) {
  is SyncFailure.AuthExpired -> stringResource(Res.string.sync_status_auth_expired_body)
  is SyncFailure.Hydration -> stringResource(
    Res.string.sync_status_hydration_error_body,
    kind.wireName,
    failedAttempts,
  )

  is SyncFailure.Push -> stringResource(Res.string.sync_status_push_error_body)
}

@Composable
private fun StatusRow(
  icon: ImageVector,
  title: String,
  body: String,
  tint: Color,
  container: Color,
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
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
