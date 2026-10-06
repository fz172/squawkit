package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_add_count
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_footer_disclaimer
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_select_to_add
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_selected_count

/**
 * The review's pinned footer (1d, 2b): one primary button that says how many it will add, and the
 * liability line (PRD §4.9) beside it. No *Skip*: back already says no. On a phone the line sits
 * above a full-width button; on a wide layout it sits at the start, with the count and the button
 * at the end.
 */
@Composable
internal fun ReviewFooter(
  selected: Int,
  wide: Boolean,
  isSaving: Boolean,
  onAdd: () -> Unit,
) {
  val taskNoun = LocalThingLexicon.current.taskNoun
  val label = if (selected == 0) {
    stringResource(
      Res.string.suggestions_select_to_add,
      LexiconFormatter.plural(taskNoun)
    )
  } else {
    stringResource(
      Res.string.suggestions_add_count,
      selected,
      if (selected == 1) {
        LexiconFormatter.titleCase(taskNoun)
      } else {
        LexiconFormatter.titleCasePlural(taskNoun)
      },
    )
  }
  val disclaimer = stringResource(Res.string.suggestions_footer_disclaimer)
  Surface(color = MaterialTheme.colorScheme.background) {
    Column(Modifier.navigationBarsPadding()) {
      HorizontalDivider(
        thickness = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
      )
      if (wide) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(
              horizontal = Spacing.extraLarge,
              vertical = Spacing.medium
            ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        ) {
          Text(
            text = disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          if (selected > 0) {
            Text(
              text = stringResource(
                Res.string.suggestions_selected_count,
                selected
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          AddButton(label, selected, isSaving, onAdd, Modifier.height(44.dp))
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.large, vertical = Spacing.medium),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          Text(
            text = disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          AddButton(
            label,
            selected,
            isSaving,
            onAdd,
            Modifier
              .fillMaxWidth()
              .height(52.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun AddButton(
  label: String,
  selected: Int,
  isSaving: Boolean,
  onAdd: () -> Unit,
  modifier: Modifier,
) {
  Button(
    onClick = onAdd,
    enabled = selected > 0 && !isSaving,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    modifier = modifier,
  ) {
    if (isSaving) {
      CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp
      )
    } else if (selected > 0) {
      Icon(
        Icons.Default.Add,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
      )
    }
    if (isSaving || selected > 0) Spacer(Modifier.width(Spacing.small))
    Text(label, maxLines = 1)
  }
}
