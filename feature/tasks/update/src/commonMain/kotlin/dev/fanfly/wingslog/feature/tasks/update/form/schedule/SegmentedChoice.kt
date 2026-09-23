package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * A single-choice row in one container: every option on screen at once, a touch target tall, the
 * picked one filled. What the schedule's choices are — labelled choices, not steps and not cards.
 */
@Composable
internal fun <T> SegmentedChoice(
  options: List<SegmentOption<T>>,
  selected: T?,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(Spacing.smallCornerRadius)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
      .padding(Spacing.extraSmall)
      .selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    options.forEach { option ->
      val active = option.value == selected
      val content =
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
      Row(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = LocalMinimumInteractiveComponentSize.current - Spacing.small)
          .clip(shape)
          .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
          .selectable(selected = active, role = Role.RadioButton) {
            onSelect(
              option.value
            )
          }
          .padding(horizontal = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(
          Spacing.extraSmall,
          Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (option.icon != null) {
          Icon(
            option.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(Spacing.large),
          )
        }
        Text(
          option.label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = content,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
