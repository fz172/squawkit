package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.normalizeInviteCode
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.continue_action
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_field_label
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_instructions
import wingslog.feature.sharing.sharedassets.generated.resources.enter_code_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

private const val CODE_LENGTH = 8

/**
 * Type-a-code entry point for redeeming a share (#209), presented as a centered dialog over the
 * fleet. Purely presentational: it validates the code is well-formed and hands the raw value back
 * via [onSubmit]. The submit path parks the code on the same channel a deep link uses, so the
 * confirmation/loading, guest park-and-resume, and error copy are the redeem flow's, not this
 * dialog's.
 *
 * Deliberately does NOT preview as-you-type: preview spends the same failed-attempt budget as redeem
 * (#164), so a per-keystroke lookup would lock a user out of their own invite. Validation here is
 * purely local (shape only); the server is consulted once, on submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterInviteCodeScreen(
  onSubmit: (rawCode: String) -> Unit,
  onDismiss: () -> Unit,
) {
  // Stored canonical: uppercase, alphabet-only, ≤8 — the dash is display-only (formatInviteCode).
  var code by remember { mutableStateOf("") }
  val isComplete = normalizeInviteCode(code) != null
  val submit = { if (isComplete) onSubmit(code) }

  // The nav dialog destination gives us a transparent, scrimmed window over the fleet; we center our
  // own card in it so the dialog looks the same on a phone and a wide window (not full-screen).
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(Spacing.large),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
      shadowElevation = 12.dp,
      modifier = Modifier
        .widthIn(max = 400.dp)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
        Spacer(Modifier.height(Spacing.large))
        Text(
          text = stringResource(Res.string.enter_code_title),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.small))
        Text(
          text = stringResource(Res.string.enter_code_instructions),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.large))
        OutlinedTextField(
          // The field's value is the raw, undashed code — the EFA1-GGTH dash is display-only, applied
          // by the visual transformation. Formatting the value string directly instead desyncs the
          // cursor from the text (the inserted dash shifts the caret back a slot as you type).
          value = code,
          onValueChange = { raw ->
            // Keep only alphabet characters (uppercased), drop any stray input, and cap at the code
            // length. normalizeInviteCode stays the single source of truth for what a code *is*;
            // this just bounds what the field will hold as it's typed.
            code = raw.uppercase().filter { it in INPUT_ALPHABET }.take(CODE_LENGTH)
          },
          label = { Text(stringResource(Res.string.enter_code_field_label)) },
          singleLine = true,
          visualTransformation = InviteCodeGroupingTransformation,
          textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { submit() }),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.large))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) {
            Text(stringResource(CoreRes.string.cancel))
          }
          Spacer(Modifier.width(Spacing.small))
          Button(onClick = submit, enabled = isComplete) {
            Text(stringResource(CoreRes.string.continue_action))
          }
        }
      }
    }
  }
}

/** The code alphabet plus the visually-ambiguous characters we tolerate on input. */
private const val INPUT_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"

/**
 * Renders the EFA1-GGTH grouping as display-only, over a raw (undashed) field value. The dash is
 * inserted after the 4th character once there's a 5th; the offset mapping shifts every caret
 * position past it by one so the cursor tracks the raw text instead of jumping when the dash appears.
 */
private val InviteCodeGroupingTransformation = VisualTransformation { text ->
  val raw = text.text
  val formatted = if (raw.length > 4) "${raw.take(4)}-${raw.drop(4)}" else raw
  val mapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = if (offset <= 4) offset else offset + 1
    override fun transformedToOriginal(offset: Int): Int = if (offset <= 4) offset else offset - 1
  }
  TransformedText(AnnotatedString(formatted), mapping)
}
