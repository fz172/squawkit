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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.delete
import wingslog.core.ui.generated.resources.save_changes

@Composable
fun BottomButtons(
  onPrimaryClick: () -> Unit,
  onSecondaryClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  onDangerClick: (() -> Unit)? = null,
  dangerLabel: String = stringResource(Res.string.delete),
  primaryEnabled: Boolean = true,
  secondaryEnabled: Boolean = true,
  isPrimaryFunctionInProgress: Boolean = false,
  primaryLabel: String = stringResource(Res.string.save_changes),
  secondaryLabel: String = stringResource(Res.string.cancel),
) {
  Box(
    modifier = modifier.fillMaxWidth().background(Color.Transparent)
      .padding(Spacing.screenPadding),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // 1. Cancel Button (optional)
      if (onSecondaryClick != null) {
        OutlinedButton(
          onClick = onSecondaryClick,
          enabled = secondaryEnabled && !isPrimaryFunctionInProgress,
          modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline
          ),
        ) {
          Text(
            text = secondaryLabel.uppercase(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      // 2. Delete Button (Optional)
      if (onDangerClick != null) {
        OutlinedButton(
          onClick = onDangerClick,
          enabled = !isPrimaryFunctionInProgress,
          modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.error,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline
          ),
          border = BorderStroke(
            Spacing.hairline,
            if (!isPrimaryFunctionInProgress) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outline
          )
        ) {
          Text(
            text = dangerLabel.uppercase(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      // 3. Save Button
      Button(
        onClick = onPrimaryClick,
        modifier = Modifier.weight(1f).height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          disabledContentColor = MaterialTheme.colorScheme.outline
        ),
        enabled = primaryEnabled && !isPrimaryFunctionInProgress,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (isPrimaryFunctionInProgress) {
            CircularProgressIndicator(
              modifier = Modifier.size(Spacing.large),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.small))
          }
          Text(
            text = primaryLabel.uppercase(),
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
    onPrimaryClick = {},
    onSecondaryClick = {},
    onDangerClick = {})
}
