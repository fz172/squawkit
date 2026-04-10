package dev.fanfly.wingslog.feature.inspection.update.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun InspectionDetailTab(
  refNumber: String,
  onRefNumberChange: (String) -> Unit,
  complianceAuthority: String,
  onComplianceAuthorityChange: (String) -> Unit,
  complianceNotes: String,
  onComplianceNotesChange: (String) -> Unit,
  attachmentSection: @Composable () -> Unit,
) {
  DocumentationFields(
    refNumber = refNumber,
    onRefNumberChange = onRefNumberChange,
    complianceAuthority = complianceAuthority,
    onComplianceAuthorityChange = onComplianceAuthorityChange,
    complianceNotes = complianceNotes,
    onComplianceNotesChange = onComplianceNotesChange
  )

  Spacer(modifier = Modifier.height(Spacing.large))

  attachmentSection()
}
