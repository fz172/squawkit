package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.tasks.suggestions.model.SuggestionItem
import dev.fanfly.wingslog.thing.ThingTemplate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.edit
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestion_more_options
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_clear
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_edited
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_group_count
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_select_all
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * A section's header (1d), sitting on its card: its name with how many are picked of how many,
 * and one action for the whole section, *Select all* until every row is picked, then *Clear*.
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
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(
        Res.string.suggestions_group_count,
        title,
        selected,
        total
      ).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    // A text action, not a button: a button's height would push the header off its card.
    Text(
      text = stringResource(
        if (selected == total) {
          Res.string.suggestions_clear
        } else {
          Res.string.suggestions_select_all
        },
      ),
      style = MaterialTheme.typography.labelLarge,
      color = if (enabled) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier
        .clip(RoundedCornerShape(Spacing.badgeCornerRadius))
        .clickable(enabled = enabled, role = Role.Button, onClick = onToggleAll)
        .padding(horizontal = Spacing.extraSmall, vertical = Spacing.small),
    )
  }
}

/**
 * One suggestion (1d, 2b): the checkbox leads, on the title's line, and picks it. Closed, a phone
 * row is the title, the interval in mono (the part worth scanning for) and where it comes from; a
 * wide row adds what the task is, with the interval and the source in fixed columns, so the list
 * scans like a table.
 *
 * Tapping anywhere else on the row opens or closes it, as the chevron at its end says (R28): what
 * the task is, its interval to change in place, and *More options* for the whole task form. The
 * open row is tinted.
 */
@Composable
internal fun SuggestionRow(
  item: SuggestionItem,
  template: ThingTemplate?,
  wide: Boolean,
  enabled: Boolean,
  expanded: Boolean,
  onToggle: () -> Unit,
  onExpandedChange: (Boolean) -> Unit,
  onSource: () -> Unit,
  onInterval: (IntervalEdit) -> Unit,
  onMoreOptions: () -> Unit,
) {
  val edited = item.edited
  val title = edited?.title ?: item.suggestion.title
  val rule =
    ruleText(edited?.rules ?: item.suggestion.rules, template)?.uppercase()
  val description = edited?.notes ?: item.suggestion.description
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(
        if (expanded) {
          Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = OpenRowTint))
        } else {
          Modifier
        },
      )
      .clickable(
        enabled = enabled,
        onClickLabel = stringResource(CoreRes.string.edit),
        onClick = { onExpandedChange(!expanded) },
      )
      .padding(
        start = Spacing.large,
        end = Spacing.small,
        top = 14.dp,
        bottom = 14.dp
      ),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    // The box alone picks the row, centred on the title's first line; its touch target spills
    // past the 24dp it takes in the layout.
    Box(
      modifier = Modifier.size(width = CheckSize, height = TitleLine),
      contentAlignment = Alignment.Center,
    ) {
      Checkbox(
        checked = item.selected,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = Modifier.wrapContentSize(unbounded = true),
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      if (!wide && !expanded && rule != null) RuleLine(rule)
      if ((wide || expanded) && description.isNotEmpty()) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (expanded) {
        IntervalFields(
          rules = edited?.rules ?: item.suggestion.rules,
          template = template,
          enabled = enabled,
          onInterval = onInterval,
          modifier = Modifier.padding(top = Spacing.small),
        )
      }
      if (!wide) {
        Tags(
          item,
          edited != null,
          onSource,
          Modifier.padding(top = Spacing.extraSmall)
        )
      }
      if (expanded) {
        Text(
          text = stringResource(Res.string.suggestion_more_options),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .clickable(
              enabled = enabled,
              role = Role.Button,
              onClick = onMoreOptions
            )
            .padding(vertical = Spacing.small),
        )
      }
    }
    if (wide) {
      Box(Modifier.width(RuleColumn), contentAlignment = Alignment.TopEnd) {
        if (rule != null) RuleLine(rule)
      }
      Box(Modifier.width(SourceColumn), contentAlignment = Alignment.TopEnd) {
        Tags(item, edited != null, onSource)
      }
    }
    // Says what a tap on the row does; the row itself takes the tap.
    Box(
      modifier = Modifier.size(width = CheckSize, height = TitleLine),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
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
  item: SuggestionItem,
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
        text = stringResource(Res.string.suggestions_edited).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** The checkbox's footprint, and the title's line it sits on (titleSmall). */
private val CheckSize = 24.dp
private val TitleLine = 20.dp

/** How much the open row is tinted with the primary color (1d). */
private const val OpenRowTint = 0.08f
private val RuleColumn = 120.dp
private val SourceColumn = 150.dp
