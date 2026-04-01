package dev.fanfly.wingslog.core.ui.common.compose

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.save_changes


/**
 * Primary + cancel action buttons, pinned to the bottom of a screen.
 * Buttons are side-by-side with equal width and a transparent background,
 * matching the LogDetailsBottomBar pattern.
 */
@Composable
fun BottomButtons(
  onSaveClick: () -> Unit,
  onCancelClick: () -> Unit,
  modifier: Modifier = Modifier,
  saveEnabled: Boolean = true,
  cancelEnabled: Boolean = true,
  isSaving: Boolean = false,
  saveLabel: String = stringResource(Res.string.save_changes),
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(Color.Transparent)
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      OutlinedButton(
        onClick = onCancelClick,
        enabled = cancelEnabled && !isSaving,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = MaterialTheme.colorScheme.surface,
        ),
      ) {
        Text(text = stringResource(Res.string.cancel), fontSize = 16.sp)
      }
      Button(
        onClick = onSaveClick,
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        enabled = saveEnabled && !isSaving,
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = Color.White,
            strokeWidth = 2.dp
          )
          Spacer(Modifier.width(8.dp))
        }
        Text(text = saveLabel, fontSize = 16.sp)
      }
    }
  }
}

@Preview
@Composable
fun BottomButtonsPreview() {
  BottomButtons(
    onSaveClick = {},
    onCancelClick = {})
}
