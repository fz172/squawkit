package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.update.picker.TaskPickerSheet
import dev.fanfly.wingslog.thing.MaintenanceTask
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.schedule_pick_linked_task
import wingslog.feature.tasks.update.generated.resources.schedule_with_another_work
import wingslog.feature.tasks.update.generated.resources.schedule_with_another_work_description

@Composable
internal fun AdvancedLinkedSection(
  open: Boolean,
  onToggle: () -> Unit,
  isLinkedMode: Boolean,
  linkedTask: MaintenanceTask?,
  availableInspections: List<MaintenanceTask>,
  onPick: (MaintenanceTask) -> Unit,
  onClear: () -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }

  val borderColor = if (isLinkedMode) MaterialTheme.colorScheme.primary
  else MaterialTheme.colorScheme.outlineVariant
  val rotation by animateFloatAsState(
    targetValue = if (open) 180f else 0f,
    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      ),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(
          horizontal = Spacing.large,
          vertical = Spacing.medium
        ),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Icon(
          Icons.Default.Link,
          contentDescription = null,
          tint = if (isLinkedMode) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(Spacing.large),
        )
        Text(
          stringResource(Res.string.schedule_with_another_work),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (isLinkedMode) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
        )
      }
      Icon(
        Icons.Default.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Spacing.large)
          .rotate(rotation),
      )
    }

    AnimatedVisibility(
      visible = open,
      enter = fadeIn() + expandVertically(),
      exit = fadeOut() + shrinkVertically(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Spacing.large)
          .padding(bottom = Spacing.large),
      ) {
        Spacer(Modifier.height(Spacing.small))
        Text(
          stringResource(Res.string.schedule_with_another_work_description),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.medium))
        if (linkedTask != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Spacing.cardCornerRadius))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
              .border(
                Spacing.hairline,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(Spacing.cardCornerRadius)
              )
              .padding(
                horizontal = Spacing.large,
                vertical = Spacing.medium
              ),
          ) {
            Text(
              "✓",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(end = Spacing.medium),
            )
            Text(
              linkedTask.title,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            Icon(
              Icons.Default.Close,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .size(Spacing.xLarge)
                .clip(RoundedCornerShape(Spacing.smallCornerRadius))
                .clickable { onClear() }
                .padding(Spacing.extraSmall),
            )
          }
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Spacing.smallCornerRadius))
              .border(
                1.5.dp,
                MaterialTheme.colorScheme.outline,
                RoundedCornerShape(Spacing.smallCornerRadius)
              )
              .clickable { showPicker = true }
              .padding(
                horizontal = Spacing.medium,
                vertical = Spacing.medium
              ),
          ) {
            Icon(
              Icons.Default.Add,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(Spacing.medium)
                .padding(end = Spacing.none),
            )
            Spacer(Modifier.width(Spacing.small))
            Text(
              stringResource(Res.string.schedule_pick_linked_task),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }

  if (showPicker) {
    TaskPickerSheet(
      availableCards = availableInspections,
      selectedIds = listOfNotNull(linkedTask?.id),
      onToggle = { id ->
        availableInspections.firstOrNull { it.id == id }
          ?.let { onPick(it) }
        showPicker = false
      },
      onDismiss = { showPicker = false },
      singleSelect = true,
    )
  }
}
