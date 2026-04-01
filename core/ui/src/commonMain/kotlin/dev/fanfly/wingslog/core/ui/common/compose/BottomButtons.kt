package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.save_changes

@Composable
fun BottomButtons(
  onSaveClick: () -> Unit,
  onCancelClick: () -> Unit,
  modifier: Modifier = Modifier,
  onDeleteClick: (() -> Unit)? = null,
  deleteLabel: String = stringResource(Res.string.delete),
  saveEnabled: Boolean = true,
  cancelEnabled: Boolean = true,
  isSaving: Boolean = false,
  saveLabel: String = stringResource(Res.string.save_changes),
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Transparent)
      .padding(Spacing.screenPadding),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 1. Cancel Button
      OutlinedButton(
        onClick = onCancelClick,
        enabled = cancelEnabled && !isSaving,
        modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      ) {
        Text(
          text = stringResource(Res.string.cancel).uppercase(),
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }

      // 2. Delete Button (Optional)
      if (onDeleteClick != null) {
        OutlinedButton(
          onClick = onDeleteClick,
          enabled = !isSaving,
          modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.error
          ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
          Text(
            text = deleteLabel.uppercase(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      // 3. Save Button
      Button(
        onClick = onSaveClick,
        modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        enabled = saveEnabled && !isSaving,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
          }
          Text(
            text = saveLabel.uppercase(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun BottomButtonsPreview() {
  BottomButtons(
    onSaveClick = {},
    onCancelClick = {},
    onDeleteClick = {})
}
