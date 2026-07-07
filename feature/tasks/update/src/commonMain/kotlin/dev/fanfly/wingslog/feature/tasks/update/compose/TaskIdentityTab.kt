package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.logs.sharedassets.util.displayName
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.component_type
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.compliance_ad_sub
import wingslog.feature.tasks.update.generated.resources.compliance_routine_sub
import wingslog.feature.tasks.update.generated.resources.compliance_sb_sub
import wingslog.feature.tasks.update.generated.resources.compliance_type
import wingslog.feature.tasks.update.generated.resources.compliance_type_ad_full
import wingslog.feature.tasks.update.generated.resources.compliance_type_description
import wingslog.feature.tasks.update.generated.resources.compliance_type_routine_short
import wingslog.feature.tasks.update.generated.resources.compliance_type_sb_full
import wingslog.feature.tasks.update.generated.resources.component_type_description
import wingslog.feature.tasks.update.generated.resources.task_description_placeholder
import wingslog.feature.tasks.update.generated.resources.task_title
import wingslog.feature.tasks.update.generated.resources.task_title_helper
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Identity tab for Add/Edit Maintenance Task screens.
 * Pass null for [onComponentChange] or [onComplianceTypeChange] to render those sections read-only.
 */
@Composable
fun TaskIdentityTab(
  title: String,
  onTitleChange: (String) -> Unit,
  component: ComponentType,
  onComponentChange: ((ComponentType) -> Unit)?,
  complianceType: ComplianceType,
  onComplianceTypeChange: ((ComplianceType) -> Unit)?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.massive),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
      FormTextField(
        label = stringResource(Res.string.task_title),
        value = title,
        onValueChange = onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
          capitalization = KeyboardCapitalization.Sentences, // Capitalizes the first character
          imeAction = ImeAction.Done // Changes Enter key to a Done/Action button
        ),
        placeholder = stringResource(Res.string.task_description_placeholder),
      )
      Text(
        text = stringResource(Res.string.task_title_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }

    // ── Section 2: Component Type ─────────────────────────────────────────
    val components = ComponentType.entries
      .filter { it != ComponentType.COMPONENT_UNKNOWN }

    IdentitySection(
      header = stringResource(CoreRes.string.component_type),
      description = stringResource(Res.string.component_type_description),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val componentEntries =
          if (onComponentChange != null) components else components.filter { it == component }
        componentEntries.forEach { entry ->
          val label = entry.displayName()
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
      header = stringResource(Res.string.compliance_type),
      description = stringResource(Res.string.compliance_type_description),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        val complianceEntries =
          if (onComplianceTypeChange != null) ComplianceType.entries else ComplianceType.entries.filter { it == complianceType }
        complianceEntries.forEach { entry ->
          val label = when (entry) {
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ->
              stringResource(Res.string.compliance_type_routine_short)

            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN ->
              stringResource(Res.string.compliance_type_sb_full)

            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE ->
              stringResource(Res.string.compliance_type_ad_full)
          }
          val subtitle = when (entry) {
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION ->
              stringResource(Res.string.compliance_routine_sub)

            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN ->
              stringResource(Res.string.compliance_sb_sub)

            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE ->
              stringResource(Res.string.compliance_ad_sub)
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
    FormSectionLabel(header)
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
  if (onClick == null) {
    Column(
      modifier = modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
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
    return
  }

  val interactionSource = remember { MutableInteractionSource() }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    RadioButton(
      selected = selected,
      onClick = onClick,
      colors = RadioButtonDefaults.colors(
        selectedColor = MaterialTheme.colorScheme.primary,
        unselectedColor = MaterialTheme.colorScheme.outlineVariant,
      ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
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
