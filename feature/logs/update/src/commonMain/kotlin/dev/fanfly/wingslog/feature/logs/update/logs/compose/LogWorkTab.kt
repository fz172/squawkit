package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.component_type
import wingslog.feature.logs.sharedassets.generated.resources.maintenance_date
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.component_section_description
import wingslog.feature.logs.update.generated.resources.date_section_description
import wingslog.feature.logs.update.generated.resources.tap_to_change_date
import wingslog.feature.logs.update.generated.resources.work_description_required
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.logs.sharedassets.generated.resources.Res as SharedRes

@Composable
fun LogWorkTab(
  maintenanceDate: LocalDate?,
  onDateClick: () -> Unit,
  workDescription: String,
  onWorkDescriptionChange: (String) -> Unit,
  aircraft: Aircraft?,
  selectedComponentType: ComponentType,
  onComponentTypeChange: (ComponentType) -> Unit,
  selectedSubComponent: String?,
  onSubComponentChange: (String?) -> Unit,
  error: UiText?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    LogSection(
      header = stringResource(SharedRes.string.maintenance_date),
      description = stringResource(Res.string.date_section_description),
    ) {
      val dateText = maintenanceDate?.toDisplayFormat()
        ?: stringResource(Res.string.tap_to_change_date)
      FormValueField(
        value = dateText,
        label = stringResource(SharedRes.string.maintenance_date),
        showLabel = false,
        onClick = onDateClick,
        accessibilityDescription = stringResource(SharedRes.string.maintenance_date),
        leadingIcon = {
          Icon(Icons.Default.CalendarToday, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    LogSection(
      header = stringResource(CoreRes.string.component_type),
      description = stringResource(Res.string.component_section_description),
    ) {
      ComponentSection(
        aircraft = aircraft,
        selectedComponentType = selectedComponentType,
        selectedSubComponent = selectedSubComponent,
        onComponentTypeChange = onComponentTypeChange,
        onSubComponentChange = onSubComponentChange,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    FormTextField(
      value = workDescription,
      onValueChange = onWorkDescriptionChange,
      label = stringResource(Res.string.work_description_required),
      modifier = Modifier.fillMaxWidth(),
      singleLine = false,
      minLines = 4,
      maxLines = 8,
      keyboardOptions = FormKeyboard.Sentences,
      isError = error != null,
      supportingText = error?.asString(),
    )
  }
}
