package dev.fanfly.wingslog.feature.sync.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import wingslog.feature.sync.settings.generated.resources.sync_section_options
import wingslog.feature.sync.settings.generated.resources.sync_section_status
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
              val title =
                stringResource(Res.string.setting_item_sync_on_cellular)
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
