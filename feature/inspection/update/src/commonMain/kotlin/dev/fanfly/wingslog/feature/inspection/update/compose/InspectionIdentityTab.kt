package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.component_airframe
import wingslog.core.ui.generated.resources.component_avionics
import wingslog.core.ui.generated.resources.component_engine
import wingslog.core.ui.generated.resources.component_propeller
import wingslog.core.ui.generated.resources.component_type
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes
import wingslog.feature.inspection.update.generated.resources.compliance_ad_sub
import wingslog.feature.inspection.update.generated.resources.compliance_routine_sub
import wingslog.feature.inspection.update.generated.resources.compliance_sb_sub
import wingslog.feature.inspection.update.generated.resources.compliance_type
import wingslog.feature.inspection.update.generated.resources.compliance_type_ad_full
import wingslog.feature.inspection.update.generated.resources.compliance_type_description
import wingslog.feature.inspection.update.generated.resources.compliance_type_routine_short
import wingslog.feature.inspection.update.generated.resources.compliance_type_sb_full
import wingslog.feature.inspection.update.generated.resources.component_type_description
import wingslog.feature.inspection.update.generated.resources.inspection_title
import wingslog.feature.inspection.update.generated.resources.task_description_placeholder
import wingslog.feature.inspection.update.generated.resources.task_title_helper

/**
 * Identity tab for Add/Edit Inspection screens.
 * Pass null for [onComponentChange] or [onComplianceTypeChange] to render those sections read-only.
 */
@Composable
fun InspectionIdentityTab(
  title: String,
  onTitleChange: (String) -> Unit,
  component: InspectionComponentType,
  onComponentChange: ((InspectionComponentType) -> Unit)?,
  complianceType: ComplianceType,
  onComplianceTypeChange: ((ComplianceType) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    // ── Section 1: Task Title ─────────────────────────────────────────────
    IdentitySection(
      header = stringResource(InspectionRes.string.inspection_title),
      description = stringResource(InspectionRes.string.task_title_helper),
    ) {
      OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,

        placeholder = {
          Text(
            text = stringResource(InspectionRes.string.task_description_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
          )
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          focusedLabelColor = MaterialTheme.colorScheme.primary,
          unfocusedLabelColor = MaterialTheme.colorScheme.outline,
        ),
      )
    }

    // ── Section 2: Component Type ─────────────────────────────────────────
    val components = InspectionComponentType.entries
      .filter { it != InspectionComponentType.INSPECTION_COMPONENT_UNKNOWN }

    IdentitySection(
      header = stringResource(CoreRes.string.component_type),
      description = stringResource(InspectionRes.string.component_type_description),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val componentEntries =
          if (onComponentChange != null) components else components.filter { it == component }
        componentEntries.forEach { entry ->
          val label = when (entry) {
            InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME ->
              stringResource(CoreRes.string.component_airframe)

            InspectionComponentType.INSPECTION_COMPONENT_ENGINE ->
              stringResource(CoreRes.string.component_engine)

            InspectionComponentType.INSPECTION_COMPONENT_PROPELLER ->
              stringResource(CoreRes.string.component_propeller)

            InspectionComponentType.INSPECTION_COMPONENT_AVIONICS ->
              stringResource(CoreRes.string.component_avionics)

            else -> entry.name.removePrefix("INSPECTION_COMPONENT_")
          }
          IdentityRadioItem(
            label = label,
            selected = component == entry,
            onClick = onComponentChange?.let { cb -> { cb(entry) } },
          )
        }
      }
    }

    // ── Section 3: Compliance Type ────────────────────────────────────────
    IdentitySection(
      header = stringResource(InspectionRes.string.compliance_type),
      description = stringResource(InspectionRes.string.compliance_type_description),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val complianceEntries =
          if (onComplianceTypeChange != null) ComplianceType.entries else ComplianceType.entries.filter { it == complianceType }
        complianceEntries.forEach { entry ->
          val label = when (entry) {
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ->
              stringResource(InspectionRes.string.compliance_type_routine_short)

            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN ->
              stringResource(InspectionRes.string.compliance_type_sb_full)

            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE ->
              stringResource(InspectionRes.string.compliance_type_ad_full)
          }
          val subtitle = when (entry) {
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ->
              stringResource(InspectionRes.string.compliance_routine_sub)

            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN ->
              stringResource(InspectionRes.string.compliance_sb_sub)

            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE ->
              stringResource(InspectionRes.string.compliance_ad_sub)
          }
          IdentityRadioItem(
            label = label,
            subtitle = subtitle,
            selected = complianceType == entry,
            onClick = onComplianceTypeChange?.let { cb -> { cb(entry) } },
          )
        }
      }
    }
  }
}

// ── Private helpers ───────────────────────────────────────────────────────────

@Composable
private fun IdentitySection(
  header: String,
  description: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(
      text = header.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      letterSpacing = 1.2.sp,
    )
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )
    content()
  }
}

@Composable
private fun IdentityRadioItem(
  label: String,
  selected: Boolean,
  onClick: (() -> Unit)?,
  subtitle: String = "",
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    RadioButton(
      selected = selected,
      onClick = onClick,
      enabled = onClick != null,
      colors = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.outlineVariant,
        disabledSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        disabledUnselectedColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tiny)) {
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
