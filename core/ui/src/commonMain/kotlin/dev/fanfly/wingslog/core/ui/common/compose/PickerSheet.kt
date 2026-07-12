package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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


/**
 * A standardized BottomSheet template for selection/picker flows.
 *
 * Differences from [DetailSheet]:
 * - Header is simpler, usually just a title.
 * - Scrolling is handled by the caller (often a LazyColumn or a custom Column with sticky footer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  headerSlot: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.extraLarge)
        .padding(bottom = Spacing.huge),
      verticalArrangement = Arrangement.spacedBy(Spacing.none),
    ) {
      // Header Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        headerSlot()
      }
      content()
    }
  }
}

enum class PickerSelectionMode {
  CHECKBOX,
  RADIO,
}

@Composable
fun PickerSectionHeader(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(
      top = Spacing.large,
      bottom = Spacing.small
    ),
  )
}

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

@Composable
fun PickerDoneButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
  ) {
    Text(text)
  }
}

@Composable
fun PickerActionButton(
  text: String,
  onClick: () -> Unit,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  TextButton(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.small),
  ) {
    Icon(imageVector = icon, contentDescription = null)
    Spacer(Modifier.width(Spacing.small))
    Text(text)
  }
}
