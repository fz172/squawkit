package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.tasks.suggestions.model.StarterPackItem
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_edited
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_clear
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_group_count
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_select_all
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * A section's header (1d): its name with how many are picked of how many, and one action for the
 * whole section, *Select all* until every row is picked, then *Clear*.
 */
@Composable
internal fun SuggestionGroupHeader(
  title: String,
  selected: Int,
  total: Int,
  enabled: Boolean,
  onToggleAll: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(Res.string.suggestions_group_count, title, selected, total).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onToggleAll, enabled = enabled) {
      Text(
        stringResource(
          if (selected == total) Res.string.suggestions_clear else Res.string.suggestions_select_all,
        ),
      )
    }
  }
}

/**
 * One suggestion (1d, 2b): the checkbox leads and the whole row toggles. The interval is in mono,
 * the part worth scanning for, then what the task is, then where it comes from. On a wide layout
 * the interval and the source sit in fixed columns at the end, so the list scans like a table.
 */
@Composable
internal fun SuggestionRow(
  item: StarterPackItem,
  template: ThingTemplate?,
  wide: Boolean,
  enabled: Boolean,
  onToggle: () -> Unit,
  onSource: () -> Unit,
  onEdit: () -> Unit,
) {
  val edited = item.edited
  val title = edited?.title ?: item.suggestion.title
  val rule = ruleText(edited?.rules ?: item.suggestion.rules, template)?.uppercase()
  val description = edited?.notes ?: item.suggestion.description
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .toggleable(
        value = item.selected,
        enabled = enabled,
        role = Role.Checkbox,
        onValueChange = { onToggle() },
      )
      .padding(start = Spacing.extraSmall, end = Spacing.extraSmall, top = Spacing.extraSmall),
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    // The row is the toggle; the box only shows it.
    Checkbox(checked = item.selected, onCheckedChange = null, enabled = enabled)
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(top = Spacing.medium, bottom = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (!wide && rule != null) RuleLine(rule)
      if (description.isNotEmpty()) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!wide) {
        Tags(item, edited != null, onSource, Modifier.padding(top = 2.dp))
      }
    }
    if (wide) {
      Box(
        modifier = Modifier
          .width(RuleColumn)
          .padding(top = Spacing.medium),
        contentAlignment = Alignment.TopEnd,
      ) {
        if (rule != null) RuleLine(rule)
      }
      Box(
        modifier = Modifier
          .width(SourceColumn)
          .padding(top = Spacing.medium),
        contentAlignment = Alignment.TopEnd,
      ) {
        Tags(item, edited != null, onSource)
      }
    }
    // R28: change it before adding it.
    IconButton(onClick = onEdit, enabled = enabled) {
      Icon(
        Icons.Outlined.Edit,
        contentDescription = stringResource(CoreRes.string.edit),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
private fun RuleLine(rule: String) {
  Text(
    text = rule,
    style = WingslogTypography.dataSmall,
    color = MaterialTheme.colorScheme.primary,
  )
}

/** The source tag, and *Edited* once the user has changed the suggestion (R28). */
@Composable
private fun Tags(
  item: StarterPackItem,
  edited: Boolean,
  onSource: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SourceTag(item.suggestion, onSource)
    if (edited) {
      Text(
        text = stringResource(Res.string.starter_pack_edited).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private val RuleColumn = 120.dp
private val SourceColumn = 150.dp
