package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.inspection.sharedassets.generated.resources.compliance_authority
import wingslog.feature.inspection.update.generated.resources.compliance_authority_hint
import wingslog.feature.inspection.update.generated.resources.compliance_notes
import wingslog.feature.inspection.update.generated.resources.compliance_notes_hint
import wingslog.feature.inspection.update.generated.resources.reference_number
import wingslog.feature.inspection.update.generated.resources.reference_number_hint
import wingslog.feature.inspection.sharedassets.generated.resources.Res as SharedInspectionRes
import wingslog.feature.inspection.update.generated.resources.Res as InspectionRes

@Composable
fun DocumentationFields(
  refNumber: String,
  onRefNumberChange: (String) -> Unit,
  complianceAuthority: String,
  onComplianceAuthorityChange: (String) -> Unit,
  complianceNotes: String,
  onComplianceNotesChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    OutlinedTextField(
      value = refNumber,
      onValueChange = onRefNumberChange,
      label = { Text(stringResource(InspectionRes.string.reference_number)) },
      placeholder = { Text(stringResource(InspectionRes.string.reference_number_hint)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )
    Spacer(modifier = Modifier.height(Spacing.medium))
    OutlinedTextField(
      value = complianceAuthority,
      onValueChange = onComplianceAuthorityChange,
      label = { Text(stringResource(SharedInspectionRes.string.compliance_authority)) },
      placeholder = { Text(stringResource(InspectionRes.string.compliance_authority_hint)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true
    )
    Spacer(modifier = Modifier.height(Spacing.medium))

    OutlinedTextField(
      value = complianceNotes,
      onValueChange = onComplianceNotesChange,
      label = { Text(stringResource(InspectionRes.string.compliance_notes)) },
      placeholder = { Text(stringResource(InspectionRes.string.compliance_notes_hint)) },
      modifier = Modifier.fillMaxWidth(),
      minLines = 3
    )
  }
}
