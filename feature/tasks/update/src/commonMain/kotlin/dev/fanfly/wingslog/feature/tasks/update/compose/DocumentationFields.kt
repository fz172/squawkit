package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.sharedassets.generated.resources.Res
import wingslog.feature.tasks.sharedassets.generated.resources.compliance_authority
import wingslog.feature.tasks.update.generated.resources.compliance_authority_hint
import wingslog.feature.tasks.update.generated.resources.compliance_notes
import wingslog.feature.tasks.update.generated.resources.compliance_notes_hint
import wingslog.feature.tasks.update.generated.resources.reference_number
import wingslog.feature.tasks.update.generated.resources.reference_number_hint
import wingslog.feature.tasks.update.generated.resources.Res as TaskRes

@Composable
fun DocumentationFields(
  refNumber: String,
  onRefNumberChange: (String) -> Unit,
  complianceAuthority: String,
  onComplianceAuthorityChange: (String) -> Unit,
  complianceNotes: String,
  onComplianceNotesChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    FormTextField(
      value = complianceAuthority,
      onValueChange = onComplianceAuthorityChange,
      label = stringResource(Res.string.compliance_authority),
      placeholder = stringResource(TaskRes.string.compliance_authority_hint),
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = FormKeyboard.SentencesNext,
    )
    Spacer(modifier = Modifier.height(Spacing.medium))

    FormTextField(
      value = refNumber,
      onValueChange = onRefNumberChange,
      label = stringResource(TaskRes.string.reference_number),
      placeholder = stringResource(TaskRes.string.reference_number_hint),
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = FormKeyboard.CharactersNext,
    )
    Spacer(modifier = Modifier.height(Spacing.medium))

    FormTextField(
      value = complianceNotes,
      onValueChange = onComplianceNotesChange,
      label = stringResource(TaskRes.string.compliance_notes),
      placeholder = stringResource(TaskRes.string.compliance_notes_hint),
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = FormKeyboard.Sentences,
      singleLine = false,
      minLines = 3,
    )
  }
}
