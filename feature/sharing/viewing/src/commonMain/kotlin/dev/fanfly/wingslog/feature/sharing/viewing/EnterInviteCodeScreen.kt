package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.formatInviteCode
import dev.fanfly.wingslog.feature.sharing.model.normalizeInviteCode
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.continue_action
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_field_label
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_instructions
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

private const val CODE_LENGTH = 8

/**
 * Type-a-code entry point for redeeming a share (#209). Purely presentational: it validates the code
 * is well-formed and hands the raw value back via [onSubmit]. The submit path parks the code on the
 * same channel a deep link uses, so the confirmation sheet, guest park-and-resume, and error copy
 * are the redeem flow's, not this screen's.
 *
 * Deliberately does NOT preview as-you-type: preview spends the same failed-attempt budget as redeem
 * (#164), so a per-keystroke lookup would lock a user out of their own invite. Validation here is
 * purely local (shape only); the server is consulted once, on submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterInviteCodeScreen(
  onSubmit: (rawCode: String) -> Unit,
  onBack: () -> Unit,
) {
  // Stored canonical: uppercase, alphabet-only, ≤8 — the dash is display-only (formatInviteCode).
  var code by remember { mutableStateOf("") }
  val isComplete = normalizeInviteCode(code) != null
  val submit = { if (isComplete) onSubmit(code) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.enter_code_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              Icons.Filled.Close,
              contentDescription = stringResource(CoreRes.string.back)
            )
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .padding(Spacing.screenPadding)
        .fillMaxWidth(),
    ) {
      Text(
        text = stringResource(Res.string.enter_code_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(Spacing.large))
      OutlinedTextField(
        value = formatInviteCode(code),
        onValueChange = { raw ->
          // Keep only alphabet characters (uppercased), drop the display dash and any stray input,
          // and cap at the code length. normalizeInviteCode stays the single source of truth for
          // what a code *is*; this just bounds what the field will hold as it's typed.
          code = raw.uppercase()
            .filter { it in INPUT_ALPHABET }
            .take(CODE_LENGTH)
        },
        label = { Text(stringResource(Res.string.enter_code_field_label)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(Spacing.large))
      Button(
        onClick = submit,
        enabled = isComplete,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(CoreRes.string.continue_action))
      }
    }
  }
}

/** The code alphabet plus the visually-ambiguous characters we tolerate on input. */
private const val INPUT_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
