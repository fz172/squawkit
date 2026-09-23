package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun PickerSelectableRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  selectionMode: PickerSelectionMode = PickerSelectionMode.RADIO,
  titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  titleWeight: FontWeight? = null,
  titleMaxLines: Int = Int.MAX_VALUE,
  titleOverflow: TextOverflow = TextOverflow.Clip,
  subtitleMaxLines: Int = Int.MAX_VALUE,
  subtitleOverflow: TextOverflow = TextOverflow.Clip,
  /** Optional trailing chip, e.g. marking a row whose data comes from somewhere else. */
  badge: String? = null,
) {
  val rowModifier = when (selectionMode) {
    PickerSelectionMode.CHECKBOX -> Modifier.toggleable(
      value = selected,
      role = Role.Checkbox,
      onValueChange = { onClick() },
    )

    PickerSelectionMode.RADIO -> Modifier.selectable(
      selected = selected,
      role = Role.RadioButton,
      onClick = onClick,
    )
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.smallCornerRadius))
      .then(rowModifier)
      .padding(horizontal = Spacing.small, vertical = Spacing.medium),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    PickerSelectionIcon(
      selected = selected,
      selectionMode = selectionMode,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = titleStyle,
        fontWeight = titleWeight,
        maxLines = titleMaxLines,
        overflow = titleOverflow,
      )
      if (!subtitle.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(Spacing.extraSmall))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = subtitleMaxLines,
          overflow = subtitleOverflow,
        )
      }
    }
    if (badge != null) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(Spacing.smallCornerRadius),
      ) {
        Text(
          text = badge,
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(
            horizontal = Spacing.small,
            vertical = Spacing.extraSmall,
          ),
        )
      }
    }
  }
}

@Composable
private fun PickerSelectionIcon(
  selected: Boolean,
  selectionMode: PickerSelectionMode,
) {
  val icon: ImageVector = when {
    selectionMode == PickerSelectionMode.RADIO && selected -> Icons.Default.RadioButtonChecked
    selectionMode == PickerSelectionMode.RADIO -> Icons.Default.RadioButtonUnchecked
    selected -> Icons.Default.CheckBox
    else -> Icons.Default.CheckBoxOutlineBlank
  }
  Icon(
    imageVector = icon,
    contentDescription = null,
    tint = if (selected) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
  )
}
