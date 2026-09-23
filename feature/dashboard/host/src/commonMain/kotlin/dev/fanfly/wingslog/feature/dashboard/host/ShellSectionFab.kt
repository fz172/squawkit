package dev.fanfly.wingslog.feature.dashboard.host

import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.logNoun
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.adaptive.shell.ShellSection
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogUploadFab
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.sharedassets.generated.resources.add_log
import wingslog.feature.squawk.sharedassets.generated.resources.add_squawk
import wingslog.feature.tasks.sharedassets.generated.resources.add_task
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes
import wingslog.feature.tasks.sharedassets.generated.resources.Res as TasksRes

/**
 * The per-section floating action button for the adaptive shell's `sectionFab` slot: Add Squawk /
 * Task / Log for the matching section, navigating into the same add screens that
 * [ThingSectionContent]'s `onAction` uses. Dashboard has no primary add action and Settings is
 * global, so neither shows a FAB. Returns nothing until a thing is selected — the add routes are
 * all thing-scoped.
 *
 * Lives here (not in `core:ui`) because the shell cannot depend on the feature add-screen routes; it
 * is rendered inside the shell's own Scaffold FAB slot so snackbars offset around it.
 */
@Composable
fun ShellSectionFab(
  section: ShellSection,
  thingId: String?,
  navController: NavController,
  /**
   * False for a thing whose DNA this build cannot interpret. Every add route leads to a form built
   * from the template, so offering one here would write under rules we cannot read (design §6.2).
   */
  renderable: Boolean = true,
  onLinkAccount: () -> Unit = {},
) {
  if (thingId == null || !renderable) return
  when (section) {
    ShellSection.DATA_LOGS -> DataLogUploadFab(
      thingId = ThingId(thingId),
      onLinkAccount = onLinkAccount
    )

    ShellSection.SQUAWKS ->
      SectionAddFab(
        label = stringResource(
          SquawkRes.string.add_squawk,
          LocalThingLexicon.current.squawkNoun.singular,
        ),
        onClick = {
          navController.navigate(
            Screen.AddSquawk.createRoute(
              thingId
            )
          )
        },
      )

    ShellSection.TASKS ->
      SectionAddFab(
        label = stringResource(TasksRes.string.add_task),
        onClick = {
          navController.navigate(
            Screen.AddMaintenanceTask.createRoute(
              thingId
            )
          )
        },
      )

    ShellSection.LOGS ->
      SectionAddFab(
        label = stringResource(
          LogsRes.string.add_log,
          LocalThingLexicon.current.logNoun.singular,
        ),
        onClick = {
          navController.navigate(
            Screen.AddMaintenanceLog.createRoute(
              thingId
            )
          )
        },
      )

    ShellSection.DASHBOARD, ShellSection.SETTINGS -> Unit
  }
}
