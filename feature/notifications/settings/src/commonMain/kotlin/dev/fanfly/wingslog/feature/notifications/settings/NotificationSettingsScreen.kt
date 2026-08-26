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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.SwitchRowCard
import dev.fanfly.wingslog.core.ui.common.compose.SwitchRowItem
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.collaborationEnabled
import dev.fanfly.wingslog.feature.notifications.model.priorityDueEnabled
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_off
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_on
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_open_settings
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_denied_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_body
import wingslog.feature.notifications.settings.generated.resources.notification_settings_banner_unsupported_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_collaboration_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_collaboration_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_priority_due_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_priority_due_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_priority_due_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_save_error
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_signin_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_cta
import wingslog.feature.notifications.settings.generated.resources.notification_settings_sync_off_footer
import wingslog.feature.notifications.settings.generated.resources.notification_settings_title

/**
 * The real notifications settings screen (design §9.1–9.4), simplified to three toggles total
 * (design decision, 2026-08-26): the master switch, plus one row each for the two independent
 * groups below it. Priority & due updates work for anyone with OS permission — including a
 * signed-out guest, who must never see that row dimmed (§9.3, §6.8) — while collaboration needs a
 * real account with cloud sync on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
  navController: NavController,
  viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val saveErrorMessage =
    stringResource(Res.string.notification_settings_save_error)
  LaunchedEffect(state.saveError) {
    if (!state.saveError) return@LaunchedEffect
    snackbarHostState.showSnackbar(saveErrorMessage)
    viewModel.onSaveErrorShown()
  }

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(Res.string.notification_settings_title),
          onBackClick = { navController.popBackStack() },
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
          PermissionBanner(
            state = state,
            onOpenSystemSettings = viewModel::onOpenSystemSettings
          )

          SwitchRowCard(
            items = listOf(
              SwitchRowItem(
                title = stringResource(Res.string.notification_settings_all_title),
                subtitle = if (state.settings.allEnabled)
                  stringResource(Res.string.notification_settings_all_subtitle_on)
                else
                  stringResource(Res.string.notification_settings_all_subtitle_off),
                checked = state.settings.allEnabled,
                enabled = !state.isLoading,
                onCheckedChange = viewModel::onAllNotificationsToggled,
              )
            ),
          )

          val urgencyEnabled = !state.isLoading && state.settings.allEnabled
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SwitchRowCard(
              items = listOf(
                SwitchRowItem(
                  title = stringResource(Res.string.notification_settings_priority_due_title),
                  subtitle = stringResource(Res.string.notification_settings_priority_due_subtitle),
                  checked = state.settings.priorityDueEnabled,
                  enabled = urgencyEnabled,
                  onCheckedChange = viewModel::onPriorityDueToggled,
                ),
              ),
            )
            Text(
              text = stringResource(Res.string.notification_settings_priority_due_footer),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(
                top = Spacing.small,
                start = Spacing.small
              ),
            )
          }

          val collaborationEnabled =
            urgencyEnabled && state.isSignedIn && state.isCloudSyncEnabled
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            SwitchRowCard(
              items = listOf(
                SwitchRowItem(
                  title = stringResource(Res.string.notification_settings_collaboration_title),
                  subtitle = stringResource(Res.string.notification_settings_collaboration_subtitle),
                  checked = state.settings.collaborationEnabled,
                  enabled = collaborationEnabled,
                  onCheckedChange = viewModel::onCollaborationToggled,
                ),
              ),
            )
            CollaborationFooter(state = state, navController = navController)
          }

          Spacer(Modifier.height(Spacing.large))
        }
      }
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
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = colors.onContainer
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.onContainer
    )
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
