package dev.fanfly.wingslog.feature.notifications.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.ui.bar.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.grouped.GroupedLeadingIconChip
import dev.fanfly.wingslog.core.ui.grouped.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.core.ui.grouped.GroupedSwitchRow
import dev.fanfly.wingslog.core.ui.grouped.MasterSwitchRow
import dev.fanfly.wingslog.core.ui.hero.SettingsHero
import dev.fanfly.wingslog.core.ui.layout.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.collaborationEnabled
import dev.fanfly.wingslog.feature.notifications.model.priorityDueEnabled
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.notifications.settings.generated.resources.Res
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_off
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_subtitle_on
import wingslog.feature.notifications.settings.generated.resources.notification_settings_all_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_collaboration_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_collaboration_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_hero_body_off
import wingslog.feature.notifications.settings.generated.resources.notification_settings_hero_body_on
import wingslog.feature.notifications.settings.generated.resources.notification_settings_hero_title_off
import wingslog.feature.notifications.settings.generated.resources.notification_settings_hero_title_on
import wingslog.feature.notifications.settings.generated.resources.notification_settings_priority_due_subtitle
import wingslog.feature.notifications.settings.generated.resources.notification_settings_priority_due_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_save_error
import wingslog.feature.notifications.settings.generated.resources.notification_settings_title
import wingslog.feature.notifications.settings.generated.resources.notification_settings_types_label

/**
 * The real notifications settings screen (design §9.1–9.4), simplified to three toggles total
 * (design decision, 2026-08-26). Follows the house settings-screen shape — hero, master switch,
 * a labelled group of individual switches, then notes & warnings — that Backup & Sync's settings
 * screen established first: [SettingsHero] introduces the surface, [MasterSwitchRow] is visually
 * senior to the plain grouped rows below it under a "Notification types" label, and
 * [PermissionBanner] / [CollaborationFooter] — the "notes & warnings" — sit last rather than up
 * front, so a working setup reads as controls first, caveats only if one applies. Priority & due
 * updates work for anyone with OS permission — including a signed-out guest, who must never see
 * that row dimmed (§9.3, §6.8) — while collaboration needs a real account with cloud sync on.
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

        val allEnabled = state.settings.allEnabled
        Column(
          modifier = Modifier.padding(Spacing.screenPadding),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
        ) {
          SettingsHero(
            icon = if (allEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            title = stringResource(
              if (allEnabled) Res.string.notification_settings_hero_title_on
              else Res.string.notification_settings_hero_title_off
            ),
            body = stringResource(
              if (allEnabled) Res.string.notification_settings_hero_body_on
              else Res.string.notification_settings_hero_body_off
            ),
            active = allEnabled,
          )

          MasterSwitchRow(
            title = stringResource(Res.string.notification_settings_all_title),
            subtitle = if (allEnabled)
              stringResource(Res.string.notification_settings_all_subtitle_on)
            else
              stringResource(Res.string.notification_settings_all_subtitle_off),
            checked = allEnabled,
            enabled = !state.isLoading,
            onCheckedChange = viewModel::onAllNotificationsToggled,
          )

          val urgencyEnabled = !state.isLoading && allEnabled
          val collaborationEnabled =
            urgencyEnabled && state.isSignedIn && state.isCloudSyncEnabled
          GroupedSection(stringResource(Res.string.notification_settings_types_label)) {
            GroupedRowGroup(
              rows = listOf(
                {
                  val title =
                    stringResource(Res.string.notification_settings_priority_due_title)
                  GroupedSwitchRow(
                    title = title,
                    subtitle = stringResource(Res.string.notification_settings_priority_due_subtitle),
                    checked = state.settings.priorityDueEnabled,
                    enabled = urgencyEnabled,
                    onCheckedChange = viewModel::onPriorityDueToggled,
                    leading = {
                      GroupedLeadingIconChip(
                        icon = Icons.Default.PriorityHigh,
                        contentDescription = title,
                      )
                    },
                  )
                },
                {
                  val title =
                    stringResource(Res.string.notification_settings_collaboration_title)
                  GroupedSwitchRow(
                    title = title,
                    subtitle = stringResource(Res.string.notification_settings_collaboration_subtitle),
                    checked = state.settings.collaborationEnabled,
                    enabled = collaborationEnabled,
                    onCheckedChange = viewModel::onCollaborationToggled,
                    leading = {
                      GroupedLeadingIconChip(
                        icon = Icons.Default.Group,
                        contentDescription = title,
                      )
                    },
                  )
                },
              ),
            )
          }

          // Notes & warnings last (house settings-screen shape) — a working setup shows only
          // controls; a caveat appears here only when one actually applies.
          PermissionBanner(
            state = state,
            onOpenSystemSettings = viewModel::onOpenSystemSettings
          )
          CollaborationFooter(state = state, navController = navController)

          Spacer(Modifier.height(Spacing.large))
        }
      }
    }
  }
}
