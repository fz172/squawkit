package dev.fanfly.wingslog.feature.notifications.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.notifications.model.aircraftActivityEnabled
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.aogEnabled
import dev.fanfly.wingslog.feature.notifications.model.dueSoonEnabled
import dev.fanfly.wingslog.feature.notifications.model.logActivityEnabled
import dev.fanfly.wingslog.feature.notifications.model.overdueEnabled
import dev.fanfly.wingslog.feature.notifications.model.squawkActivityEnabled
import dev.fanfly.wingslog.feature.notifications.model.squawkPriorityEnabled
import dev.fanfly.wingslog.feature.notifications.model.taskActivityEnabled
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aircraft_activity_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aircraft_activity_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aog_confirm_action
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aog_confirm_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aog_confirm_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aog_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_aog_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_off
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_on
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_open_settings
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_due_soon_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_due_soon_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_group_collaboration
import wingslog.feature.notifications.settings.generated.resources.notification_settings_group_urgency
import wingslog.feature.notifications.settings.generated.resources.notification_settings_group_urgency_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_log_activity_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_log_activity_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_overdue_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_overdue_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_squawk_activity_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_squawk_activity_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_squawk_priority_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_squawk_priority_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_task_activity_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_task_activity_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The real notifications settings screen (design §9.1–9.4), replacing P1.8's "coming soon"
 * stand-in. Two independent groups: urgency alerts work for anyone with OS permission — including a
 * signed-out guest, who must never see this group dimmed (§9.3, §6.8) — while collaboration needs a
 * real account with cloud sync on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
  navController: NavController,
  viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

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
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .fillMaxSize()
          .verticalScroll(rememberScrollState()),
      ) {
        // Cosmetic half of isLoading (design §9.2) — the toggles below being disabled is the load-
        // bearing half; this is just the visible cue while PrefsState.Unresolved.
        if (state.isLoading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Column(
          modifier = Modifier.padding(Spacing.screenPadding),
          verticalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          PermissionBanner(state = state, onOpenSystemSettings = viewModel::onOpenSystemSettings)

          ToggleCard {
            ToggleRow(
              title = stringResource(Res.string.notification_settings_all_title),
              subtitle = if (state.settings.allEnabled)
                stringResource(Res.string.notification_settings_all_subtitle_on)
              else
                stringResource(Res.string.notification_settings_all_subtitle_off),
              checked = state.settings.allEnabled,
              enabled = !state.isLoading,
              onCheckedChange = viewModel::onAllNotificationsToggled,
            )
          }

          val urgencyEnabled = !state.isLoading && state.settings.allEnabled
          GroupSection(title = stringResource(Res.string.notification_settings_group_urgency)) {
            ToggleCard {
              ToggleRow(
                title = stringResource(Res.string.notification_settings_aog_title),
                subtitle = stringResource(Res.string.notification_settings_aog_subtitle),
                checked = state.settings.aogEnabled,
                enabled = urgencyEnabled,
                onCheckedChange = viewModel::onAogToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_squawk_priority_title),
                subtitle = stringResource(Res.string.notification_settings_squawk_priority_subtitle),
                checked = state.settings.squawkPriorityEnabled,
                enabled = urgencyEnabled,
                onCheckedChange = viewModel::onSquawkPriorityToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_overdue_title),
                subtitle = stringResource(Res.string.notification_settings_overdue_subtitle),
                checked = state.settings.overdueEnabled,
                enabled = urgencyEnabled,
                onCheckedChange = viewModel::onOverdueToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_due_soon_title),
                subtitle = stringResource(Res.string.notification_settings_due_soon_subtitle),
                checked = state.settings.dueSoonEnabled,
                enabled = urgencyEnabled,
                onCheckedChange = viewModel::onDueSoonToggled,
              )
            }
            Text(
              text = stringResource(Res.string.notification_settings_group_urgency_footer),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = Spacing.small, start = Spacing.small),
            )
          }

          val collaborationEnabled =
            urgencyEnabled && state.isSignedIn && state.isCloudSyncEnabled
          GroupSection(title = stringResource(Res.string.notification_settings_group_collaboration)) {
            ToggleCard {
              ToggleRow(
                title = stringResource(Res.string.notification_settings_aircraft_activity_title),
                subtitle = stringResource(Res.string.notification_settings_aircraft_activity_subtitle),
                checked = state.settings.aircraftActivityEnabled,
                enabled = collaborationEnabled,
                onCheckedChange = viewModel::onAircraftActivityToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_squawk_activity_title),
                subtitle = stringResource(Res.string.notification_settings_squawk_activity_subtitle),
                checked = state.settings.squawkActivityEnabled,
                enabled = collaborationEnabled,
                onCheckedChange = viewModel::onSquawkActivityToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_task_activity_title),
                subtitle = stringResource(Res.string.notification_settings_task_activity_subtitle),
                checked = state.settings.taskActivityEnabled,
                enabled = collaborationEnabled,
                onCheckedChange = viewModel::onTaskActivityToggled,
              )
              GroupDivider()
              ToggleRow(
                title = stringResource(Res.string.notification_settings_log_activity_title),
                subtitle = stringResource(Res.string.notification_settings_log_activity_subtitle),
                checked = state.settings.logActivityEnabled,
                enabled = collaborationEnabled,
                onCheckedChange = viewModel::onLogActivityToggled,
              )
            }
            CollaborationFooter(state = state, navController = navController)
          }

          Spacer(Modifier.height(Spacing.large))
        }
      }
    }

    if (state.confirmDisableAog) {
      AogConfirmDialog(
        onConfirm = viewModel::onConfirmDisableAog,
        onDismiss = viewModel::onDismissDisableAog,
      )
    }
  }
}

/**
 * §9.3's state table, collapsed to the two rows that actually render here — [PermissionState.GRANTED]
 * and [PermissionState.UNDETERMINED] show nothing. Neutral only: this is a convenience on top of a
 * logbook that works without it, never an error (no red, no destructive iconography).
 */
@Composable
private fun PermissionBanner(
  state: NotificationSettingsUiState,
  onOpenSystemSettings: () -> Unit,
) {
  val colors = MaterialTheme.statusColors.neutral
  val (title, body) = when (state.permission) {
    PermissionState.DENIED ->
      stringResource(Res.string.notification_settings_banner_denied_title) to
        stringResource(Res.string.notification_settings_banner_denied_body)

    PermissionState.UNSUPPORTED ->
      stringResource(Res.string.notification_settings_banner_unsupported_title) to
        stringResource(Res.string.notification_settings_banner_unsupported_body)

    PermissionState.GRANTED, PermissionState.UNDETERMINED -> return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(colors.container)
      .padding(Spacing.large),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.onContainer)
    Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.onContainer)
    // Nothing to open on web (PermissionState.UNSUPPORTED) or wherever the platform exposes no
    // deep link to its own settings page — the button would be a dead end either way.
    if (state.permission == PermissionState.DENIED && state.canOpenSystemSettings) {
      TextButton(onClick = onOpenSystemSettings) {
        Text(
          text = stringResource(Res.string.notification_settings_banner_denied_open_settings),
          color = colors.accent,
        )
      }
    }
  }
}

@Composable
private fun GroupSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = Spacing.small),
    )
    content()
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
private fun GroupDivider() {
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun ToggleRow(
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
      .padding(horizontal = Spacing.large, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
      Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
    }
    Spacer(Modifier.width(Spacing.large))
    Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
  }
}

/**
 * §9.3: signed-out and sync-off each get their own footer under collaboration only — urgency is
 * untouched either way. Signed-out takes priority when both are true; a guest turning on sync
 * before signing in still has no account for the server to attribute collaboration events to.
 */
@Composable
private fun CollaborationFooter(
  state: NotificationSettingsUiState,
  navController: NavController,
) {
  when {
    !state.isSignedIn -> FooterRow(
      body = stringResource(Res.string.notification_settings_signin_footer),
      cta = stringResource(Res.string.notification_settings_signin_cta),
      // Embedding the guest-upgrade flow here would pull feature/notifications/settings into a
      // cross-feature dependency on feature/login/upgrade for one button; the account-upgrade CTA
      // is already one tap away on the Settings row this screen was opened from.
      onClick = { navController.popBackStack() },
    )

    !state.isCloudSyncEnabled -> FooterRow(
      body = stringResource(Res.string.notification_settings_sync_off_footer),
      cta = stringResource(Res.string.notification_settings_sync_off_cta),
      onClick = { navController.navigate(Screen.SyncSettings.route) },
    )
  }
}

@Composable
private fun FooterRow(
  body: String,
  cta: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.small, vertical = Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = body,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onClick) { Text(cta) }
  }
}

/** Q5 — the one urgency toggle a pilot cannot silence quietly. */
@Composable
private fun AogConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.notification_settings_aog_confirm_title)) },
    text = { Text(stringResource(Res.string.notification_settings_aog_confirm_body)) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(
          text = stringResource(Res.string.notification_settings_aog_confirm_action),
          color = MaterialTheme.colorScheme.error,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}
