package dev.fanfly.wingslog.feature.sync.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.datetime.toDisplayTime
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRow
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSwitchRow
import dev.fanfly.wingslog.core.ui.common.compose.MasterSwitchRow
import dev.fanfly.wingslog.core.ui.common.compose.SettingsHero
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.sync.data.HydrationState
import dev.fanfly.wingslog.feature.sync.data.SyncFailure
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.sync.settings.generated.resources.Res
import wingslog.feature.sync.settings.generated.resources.setting_item_sync
import wingslog.feature.sync.settings.generated.resources.setting_item_sync_on_cellular
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_active
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_paused
import wingslog.feature.sync.settings.generated.resources.sync_hero_body_signin
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_active
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_paused
import wingslog.feature.sync.settings.generated.resources.sync_hero_title_signin
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_pending
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_title
import wingslog.feature.sync.settings.generated.resources.sync_last_synced_up_to_date
import wingslog.feature.sync.settings.generated.resources.sync_section_options
import wingslog.feature.sync.settings.generated.resources.sync_section_status
import wingslog.feature.sync.settings.generated.resources.sync_status_auth_expired_body
import wingslog.feature.sync.settings.generated.resources.sync_status_error_title
import wingslog.feature.sync.settings.generated.resources.sync_status_hydration_error_body
import wingslog.feature.sync.settings.generated.resources.sync_status_off_body
import wingslog.feature.sync.settings.generated.resources.sync_status_off_title
import wingslog.feature.sync.settings.generated.resources.sync_status_push_error_body
import wingslog.feature.sync.settings.generated.resources.sync_status_restoring
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_active
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_cellular_disabled
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_cellular_enabled
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_off
import wingslog.feature.sync.settings.generated.resources.sync_subtitle_signin
import wingslog.feature.sync.sharedassets.generated.resources.feature_name_backup_and_sync
import wingslog.feature.sync.sharedassets.generated.resources.Res as SyncRes

private val StatusDotSize = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
  navController: NavController,
  viewModel: SyncSettingsViewModel = koinViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val active = state.signedIn && state.cloudSyncEnabled

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(SyncRes.string.feature_name_backup_and_sync),
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
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
      ) {
        val (heroTitle, heroBody) = when {
          !state.signedIn ->
            Res.string.sync_hero_title_signin to Res.string.sync_hero_body_signin

          !state.cloudSyncEnabled ->
            Res.string.sync_hero_title_paused to Res.string.sync_hero_body_paused

          else -> Res.string.sync_hero_title_active to Res.string.sync_hero_body_active
        }
        SettingsHero(
          icon = if (active) Icons.Default.CloudSync else Icons.Default.CloudOff,
          title = stringResource(heroTitle),
          body = stringResource(heroBody),
          active = active,
        )

        MasterSwitchRow(
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

        GroupedSection(stringResource(Res.string.sync_section_options)) {
          GroupedRowGroup(
            rows = listOf {
              val title = stringResource(Res.string.setting_item_sync_on_cellular)
              GroupedSwitchRow(
                title = title,
                subtitle = if (state.allowUploadOnCellular)
                  stringResource(Res.string.sync_subtitle_cellular_enabled)
                else
                  stringResource(Res.string.sync_subtitle_cellular_disabled),
                checked = state.allowUploadOnCellular,
                enabled = active,
                onCheckedChange = viewModel::onAllowUploadOnCellularToggled,
                leading = {
                  GroupedLeadingIconChip(
                    icon = Icons.Default.SignalCellularAlt,
                    contentDescription = title,
                  )
                },
              )
            },
          )
        }

        GroupedSection(stringResource(Res.string.sync_section_status)) {
          GroupedRowGroup(
            rows = listOf { LastSyncedRow(state) },
          )
        }

        StatusNotes(state = state)
        Spacer(Modifier.height(Spacing.large))
      }
    }
  }
}

/**
 * When the cloud and this device last agreed, with a dot that says how things stand now: green
 * while healthy, the critical tone under a failure. The subtitle names the state in words; the
 * dot is never the only signal.
 */
@Composable
private fun LastSyncedRow(state: SyncSettingsUiState) {
  val colors = MaterialTheme.statusColors
  val hydration = state.hydration
  val (subtitle, dot) = when {
    state.failure != null -> state.failure.displayText() to colors.critical.accent
    !state.signedIn -> stringResource(Res.string.sync_subtitle_signin) to null
    !state.cloudSyncEnabled -> stringResource(Res.string.sync_status_off_title) to null
    hydration is HydrationState.InProgress ->
      stringResource(Res.string.sync_status_restoring, hydration.completed, hydration.total) to
        colors.caution.accent

    state.lastSyncedAt == null -> stringResource(Res.string.sync_last_synced_pending) to null
    else -> stringResource(Res.string.sync_last_synced_up_to_date) to colors.positive.accent
  }
  val title = stringResource(Res.string.sync_last_synced_title)
  GroupedRow(
    title = title,
    subtitle = subtitle,
    leading = {
      GroupedLeadingIconChip(
        icon = Icons.Default.Schedule,
        contentDescription = title,
      )
    },
    trailing = if (state.lastSyncedAt == null) null else ({
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        if (dot != null) {
          Box(
            modifier = Modifier
              .size(StatusDotSize)
              .clip(CircleShape)
              .background(dot),
          )
        }
        Text(
          text = state.lastSyncedAt.toDisplayTime(),
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }),
  )
}

/**
 * "Notes & warnings" (house settings-screen shape): only what the Status card cannot say in a
 * subtitle — the restore progress bar, the full explanation of what "sync is off" costs, and a
 * failure spelled out. Nothing renders when sync is caught up.
 */
@Composable
private fun StatusNotes(state: SyncSettingsUiState) {
  val colors = MaterialTheme.statusColors
  when {
    state.failure != null -> NoteRow(
      icon = Icons.Default.Warning,
      title = stringResource(Res.string.sync_status_error_title),
      body = state.failure.displayText(),
      tint = colors.critical.accent,
      container = colors.critical.container,
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

    state.signedIn && !state.cloudSyncEnabled -> NoteRow(
      icon = Icons.Default.CloudOff,
      title = stringResource(Res.string.sync_status_off_title),
      body = stringResource(Res.string.sync_status_off_body),
      tint = colors.neutral.accent,
      container = colors.neutral.container,
    )

    // Fully caught up, or a guest the hero already speaks to: no note.
    else -> Unit
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
private fun NoteRow(
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
