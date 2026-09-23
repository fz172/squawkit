package dev.fanfly.wingslog.feature.tasks.update.form.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.usesComponentTypes
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.component_type_description
import wingslog.feature.tasks.update.generated.resources.task_description_placeholder
import wingslog.feature.tasks.update.generated.resources.task_title
import wingslog.feature.tasks.update.generated.resources.task_title_helper

/**
 * Basics tab for Add/Edit Maintenance Task screens.
 * Pass null for [onComponentChange] to render that section read-only.
 * Maintenance history is only shown when [isEditing] is true — a task must exist before logs can link to it.
 */
@Composable
fun TaskIdentityTab(
  title: String,
  onTitleChange: (String) -> Unit,
  component: ComponentType,
  onComponentChange: ((ComponentType) -> Unit)?,
  modifier: Modifier = Modifier,
  isEditing: Boolean = false,
  taskId: String = "",
  availableLogs: List<MaintenanceLog> = emptyList(),
  onAddLog: () -> Unit = {},
  onRemoveLog: (MaintenanceLog) -> Unit = {},
  attachmentSection: @Composable () -> Unit = {},
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      FormTextField(
        label = stringResource(
          Res.string.task_title,
          LexiconFormatter.sentenceCase(LocalThingLexicon.current.taskNoun),
        ),
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = FormKeyboard.SentencesDone,
        placeholder = stringResource(Res.string.task_description_placeholder),
      )
      Text(
        text = stringResource(Res.string.task_title_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }

    // ── Section 2: Component Type ─────────────────────────────────────────
    //
    // Airframe / engine / propeller is `ComponentType`, and that enum is aviation's alone — see
    // [usesComponentTypes]. Every other preset gets no section: a boat and a car have parts the
    // three options cannot name, a home has none at all, and their tasks belong to the thing
    // itself, which is what a task with no component has always meant (#732).
    //
    // Nor does a locked task that was never given one: a heading over a lock note and no value
    // reads as a load that failed.
    val lockedWithoutComponent =
      onComponentChange == null && component == ComponentType.COMPONENT_UNKNOWN
    if (LocalThingTemplate.current.usesComponentTypes && !lockedWithoutComponent) {
      val components = ComponentType.entries
        .filter { it != ComponentType.COMPONENT_UNKNOWN }

      IdentitySection(
        // The lexicon's own noun: "Component" on an airplane, "Part" everywhere else. The header
        // names the thing being picked, so a fixed "Component Type" was aviation leaking into a
        // screen the template already has a word for.
        header = LexiconFormatter.sentenceCase(LocalThingLexicon.current.componentNoun),
        description = stringResource(
          Res.string.component_type_description,
          LocalThingLexicon.current.componentNoun.singular,
        ),
        lockedReason = taskLockedReason().takeIf { onComponentChange == null },
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
          val componentEntries =
            if (onComponentChange != null) components else components.filter { it == component }
          componentEntries.forEach { entry ->
            val label = entry.displayName()
            IdentityRadioItem(
              label = label,
              selected = component == entry,
              onClick = onComponentChange?.let { cb -> { cb(entry) } },
            )
          }
        }
      }
    }

    attachmentSection()

    if (isEditing) {
      MaintenanceHistorySection(
        taskId = taskId,
        availableLogs = availableLogs,
        onAddLog = onAddLog,
        onRemoveLog = onRemoveLog,
      )
    }
  }
}
