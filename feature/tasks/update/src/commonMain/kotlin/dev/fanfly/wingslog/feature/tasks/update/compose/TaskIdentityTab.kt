package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.componentNoun
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.usesComponentTypes
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceLog
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.logs.sharedassets.generated.resources.maintenance_history
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.component_type_description
import wingslog.feature.tasks.update.generated.resources.no_log_history
import wingslog.feature.tasks.update.generated.resources.task_description_placeholder
import wingslog.feature.tasks.update.generated.resources.task_title
import wingslog.feature.tasks.update.generated.resources.task_title_helper
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as LogsRes

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
          LexiconFormatter.titleCase(LocalThingLexicon.current.taskNoun),
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
    if (LocalThingTemplate.current.usesComponentTypes) {
      val components = ComponentType.entries
        .filter { it != ComponentType.COMPONENT_UNKNOWN }

      IdentitySection(
        // The lexicon's own noun: "Component" on an airplane, "Part" everywhere else. The header
        // names the thing being picked, so a fixed "Component Type" was aviation leaking into a
        // screen the template already has a word for.
        header = LexiconFormatter.titleCase(LocalThingLexicon.current.componentNoun),
        description = stringResource(
          Res.string.component_type_description,
          LocalThingLexicon.current.componentNoun.singular,
        ),
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

// ── Private helpers ───────────────────────────────────────────────────────────

@Composable
private fun MaintenanceHistorySection(
  taskId: String,
  availableLogs: List<MaintenanceLog>,
  onAddLog: () -> Unit,
  onRemoveLog: (MaintenanceLog) -> Unit,
) {
  val linkedLogs = remember(availableLogs, taskId) {
    availableLogs
      .filter { taskId in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
  }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      FormSectionLabel(stringResource(LogsRes.string.maintenance_history))
      OutlinedButton(
        onClick = onAddLog,
        contentPadding = PaddingValues(
          horizontal = Spacing.medium,
          vertical = Spacing.extraSmall,
        ),
      ) {
        Icon(
          Icons.Default.Add,
          contentDescription = null,
          modifier = Modifier.width(Spacing.large),
        )
        Spacer(Modifier.width(Spacing.extraSmall))
        Text(
          stringResource(CoreRes.string.add),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }

    if (linkedLogs.isEmpty()) {
      Text(
        text = stringResource(Res.string.no_log_history),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      linkedLogs.forEach { log ->
        val displayText = log.work_description.ifBlank { log.id }
        val logDate = log.timestamp
          ?.takeIf { it.getEpochSecond() > 0L }
          ?.toLocalDate()
          ?.toDisplayFormat()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = displayText,
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemoveLog(log) }) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(CoreRes.string.remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (logDate != null) {
            Text(
              text = logDate,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        HorizontalDivider()
      }
    }
  }
}

@Composable
internal fun IdentitySection(
  header: String,
  description: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    FormSectionLabel(header)
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )
    content()
  }
}

@Composable
internal fun IdentityRadioItem(
  label: String,
  selected: Boolean,
  onClick: (() -> Unit)?,
  subtitle: String = "",
  modifier: Modifier = Modifier,
) {
  if (onClick == null) {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        letterSpacing = 0.5.sp,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }
    return
  }

  val interactionSource = remember { MutableInteractionSource() }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    RadioButton(
      selected = selected,
      onClick = onClick,
      colors = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.outlineVariant,
      ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        letterSpacing = 0.5.sp,
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }
  }
}
