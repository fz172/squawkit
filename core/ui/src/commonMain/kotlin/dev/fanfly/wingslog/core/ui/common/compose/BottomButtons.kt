package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.save_changes


/**
 * The "Save Changes" and "Cancel" buttons, pinned to the bottom of the screen.
 */
@Composable
fun BottomButtons(
  onSaveClick: () -> Unit,
  onCancelClick: () -> Unit,
  saveEnabled: Boolean = true,
  cancelEnabled: Boolean = true
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background) // Match screen background
      .padding(horizontal = 24.dp, vertical = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Button(
      onClick = onSaveClick,
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      shape = RoundedCornerShape(12.dp),
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      enabled = saveEnabled
    ) {
      Text(text = stringResource(Res.string.save_changes), fontSize = 16.sp)
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(
      onClick = onCancelClick,
      enabled = cancelEnabled
    ) {
      Text(
        text = stringResource(Res.string.cancel),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 16.sp
      )
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
