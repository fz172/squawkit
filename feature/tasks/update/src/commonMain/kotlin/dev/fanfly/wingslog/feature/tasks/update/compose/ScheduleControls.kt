package dev.fanfly.wingslog.feature.tasks.update.compose

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.schedule_pick_linked_task
import wingslog.feature.tasks.update.generated.resources.schedule_track_calendar_time
import wingslog.feature.tasks.update.generated.resources.schedule_track_tach_hours
import wingslog.feature.tasks.update.generated.resources.schedule_unit_days
import wingslog.feature.tasks.update.generated.resources.schedule_unit_months
import wingslog.feature.tasks.update.generated.resources.schedule_unit_years
import wingslog.feature.tasks.update.generated.resources.schedule_with_another_work
import wingslog.feature.tasks.update.generated.resources.schedule_with_another_work_description

@Composable
internal fun TrackingModeChoice(
  selected: ScheduleMode?,
  onSelect: (ScheduleMode) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
    TrackingModeButton(
      icon = Icons.Default.CalendarToday,
      label = stringResource(Res.string.schedule_track_calendar_time),
      selected = selected == ScheduleMode.TIME,
      onClick = { onSelect(ScheduleMode.TIME) },
      modifier = Modifier.weight(1f),
    )
    TrackingModeButton(
      icon = Icons.Default.Schedule,
      label = stringResource(Res.string.schedule_track_tach_hours),
      selected = selected == ScheduleMode.HOURS,
      onClick = { onSelect(ScheduleMode.HOURS) },
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun TrackingModeButton(
  icon: ImageVector,
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val border = if (selected) MaterialTheme.colorScheme.primary
  else MaterialTheme.colorScheme.outlineVariant
  val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
  else MaterialTheme.colorScheme.surfaceContainer
  val content =
    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(
      Spacing.extraSmall,
      Alignment.CenterVertically
    ),
    modifier = modifier
      .heightIn(min = 80.dp)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(bg)
      .border(
        Spacing.hairline,
        border,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .clickable { onClick() }
      .padding(
        horizontal = Spacing.medium,
        vertical = Spacing.medium
      ),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = content,
      modifier = Modifier.size(Spacing.xLarge)
    )
    Text(
      label,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
      color = content,
    )
  }
}

@Composable
internal fun RecurrenceChoice(
  selected: ScheduleRecurrence?,
  options: List<Pair<ScheduleRecurrence, Pair<StringResource, StringResource>>>,
  onSelect: (ScheduleRecurrence) -> Unit,
) {
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    options.forEach { (rec, labels) ->
      val isSelected = selected == rec
      val border = if (isSelected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.outlineVariant
      val bg =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceContainer
      val labelColor = if (isSelected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.onSurface

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
          Spacing.extraSmall,
          Alignment.CenterVertically
        ),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 64.dp)
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(bg)
          .border(
            Spacing.hairline,
            border,
            RoundedCornerShape(Spacing.cardCornerRadius)
          )
          .clickable { onSelect(rec) }
          .padding(
            horizontal = Spacing.small,
            vertical = Spacing.medium
          ),
      ) {
        Text(
          stringResource(labels.first),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          color = labelColor,
          textAlign = TextAlign.Center,
        )
        Text(
          stringResource(labels.second),
          fontSize = 10.5.sp,
          lineHeight = 14.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
internal fun UnitPillSelect(
  selected: ScheduleTimeUnit,
  onSelect: (ScheduleTimeUnit) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.smallCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant,
        RoundedCornerShape(Spacing.smallCornerRadius)
      )
      .padding(Spacing.extraSmall),
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    listOf(
      ScheduleTimeUnit.DAYS to Res.string.schedule_unit_days,
      ScheduleTimeUnit.MONTHS to Res.string.schedule_unit_months,
      ScheduleTimeUnit.YEARS to Res.string.schedule_unit_years,
    ).forEach { (unit, res) ->
      val active = unit == selected
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(Spacing.smallCornerRadius))
          .background(
            if (active) MaterialTheme.colorScheme.primary
            else Color.Transparent
          )
          .clickable { onSelect(unit) }
          .padding(vertical = Spacing.small),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          stringResource(res).replaceFirstChar { it.titlecase() },
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = if (active) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
internal fun IntervalNumberInput(
  value: String,
  onChange: (String) -> Unit,
  prefix: String,
  suffix: String,
  keyboard: KeyboardType,
) {
  val borderColor = if (value.isNotBlank()) MaterialTheme.colorScheme.primary
  else MaterialTheme.colorScheme.outlineVariant

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .height(Spacing.buttonHeight)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(horizontal = Spacing.medium),
  ) {
    Text(
      prefix,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = Spacing.small),
    )
    BasicTextField(
      value = value,
      onValueChange = { v ->
        val filtered = if (keyboard == KeyboardType.Decimal) {
          v.filter { it.isDigit() || it == '.' }
        } else {
          v.filter { it.isDigit() }
        }
        onChange(filtered)
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = keyboard),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      textStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
      ),
      modifier = Modifier.weight(1f),
      decorationBox = { inner ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (value.isEmpty()) {
            Text(
              "0",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
          }
          inner()
        }
      },
    )
    Text(
      suffix,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = Spacing.small),
    )
  }
}

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
