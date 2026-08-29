package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.thing.ComplianceType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.compliance_ad_sub
import wingslog.feature.tasks.update.generated.resources.compliance_routine_sub
import wingslog.feature.tasks.update.generated.resources.compliance_sb_sub
import wingslog.feature.tasks.update.generated.resources.compliance_type
import wingslog.feature.tasks.update.generated.resources.compliance_type_ad_full
import wingslog.feature.tasks.update.generated.resources.compliance_type_description
import wingslog.feature.tasks.update.generated.resources.compliance_type_routine_short
import wingslog.feature.tasks.update.generated.resources.compliance_type_sb_full

/**
 * Compliance tab for Add/Edit Maintenance Task screens.
 * Pass null for [onComplianceTypeChange] to render that section read-only.
 */
@Composable
fun TaskComplianceTab(
  complianceType: ComplianceType,
  onComplianceTypeChange: ((ComplianceType) -> Unit)?,
  refNumber: String,
  onRefNumberChange: (String) -> Unit,
  complianceAuthority: String,
  onComplianceAuthorityChange: (String) -> Unit,
  complianceNotes: String,
  onComplianceNotesChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
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

    Spacer(modifier = Modifier.height(Spacing.massive))

    DocumentationFields(
      refNumber = refNumber,
      onRefNumberChange = onRefNumberChange,
      complianceAuthority = complianceAuthority,
      onComplianceAuthorityChange = onComplianceAuthorityChange,
      complianceNotes = complianceNotes,
      onComplianceNotesChange = onComplianceNotesChange,
    )
  }
}
