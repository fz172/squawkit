package dev.fanfly.wingslog.feature.subscription.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.PromoCodeError
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.PromoCodeUiState
import dev.fanfly.wingslog.feature.subscription.viewing.viewmodel.PromoTerm
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.subscription.viewing.generated.resources.Res
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_activating_month
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_activating_other
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_activating_quarter
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_activating_year
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_entry
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_invalid
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_offline
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_sign_in
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_subscribed
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_throttled
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_error_unverified
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_field_label
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_instructions
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_redeem
import wingslog.feature.subscription.viewing.generated.resources.subscription_promo_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * Type-a-code entry for redeeming a promo (#750).
 *
 * Purely presentational: every piece of state it shows — the code, the in-flight flag, the refusal —
 * lives in the ViewModel, so a half-typed code survives the keyboard opening, a rotation, or the web
 * app re-laying-out. It validates only that the code is *shaped* like one; whether it is redeemable
 * is a question only the server can answer, and it is asked once, on submit.
 *
 * A dialog rather than a screen because it is a detour from the paywall, not a step in it: the pilot
 * came to the page to decide about Pro, and a code is one of the two ways that decision resolves.
 */
@Composable
internal fun PromoCodeDialog(
  state: PromoCodeUiState,
  onCodeChange: (String) -> Unit,
  onSubmit: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    // Ignored while a redemption is in flight: the call is not cancellable, and dismissing here
    // would leave the pilot with no idea whether their code was spent.
    onDismissRequest = { if (!state.isSubmitting) onDismiss() },
    icon = { Icon(Icons.Default.Redeem, contentDescription = null) },
    title = { Text(stringResource(Res.string.subscription_promo_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        Text(
          text = stringResource(Res.string.subscription_promo_instructions),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          // The raw, undashed code — the grouping is display-only, applied by the visual
          // transformation below. Formatting the value itself desyncs the caret from the text.
          value = state.code,
          onValueChange = onCodeChange,
          label = { Text(stringResource(Res.string.subscription_promo_field_label)) },
          singleLine = true,
          enabled = !state.isSubmitting,
          isError = state.error != null,
          visualTransformation = PromoCodeGroupingTransformation,
          textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
          keyboardOptions = KeyboardOptions(
            // The field uppercases every keystroke anyway; asking the keyboard for caps just stops
            // the shown key caps disagreeing with what lands in the field.
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(onDone = { onSubmit() }),
          modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { error ->
          Text(
            text = stringResource(error.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onSubmit,
        enabled = state.isComplete && !state.isSubmitting,
      ) {
        if (state.isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(PROGRESS_SIZE),
            strokeWidth = PROGRESS_STROKE,
          )
        } else {
          Text(stringResource(Res.string.subscription_promo_redeem))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !state.isSubmitting) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  )
}

/**
 * The paywall's way in to [PromoCodeDialog].
 *
 * A text button, not a second filled CTA: the page already has one decision on it, and a promo code
 * is a minority path that must not read as an alternative offer. It still spans the width so it sits
 * on the same axis as the subscribe button rather than drifting off to one side.
 */
@Composable
internal fun PromoCodeEntryButton(onClick: () -> Unit) {
  TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Icon(
      imageVector = Icons.Default.Redeem,
      contentDescription = null,
      modifier = Modifier.size(Spacing.large),
    )
    Spacer(Modifier.width(Spacing.small))
    Text(stringResource(Res.string.subscription_promo_entry))
  }
}

/** The line shown while a redeemed code's entitlement is still syncing down. */
internal val PromoTerm.activatingRes: StringResource
  get() = when (this) {
    PromoTerm.ONE_MONTH -> Res.string.subscription_promo_activating_month
    PromoTerm.THREE_MONTHS -> Res.string.subscription_promo_activating_quarter
    PromoTerm.ONE_YEAR -> Res.string.subscription_promo_activating_year
    PromoTerm.OTHER -> Res.string.subscription_promo_activating_other
  }

/** The copy for each refusal. One line each: what happened, and what to do about it. */
private val PromoCodeError.messageRes: StringResource
  get() = when (this) {
    PromoCodeError.NOT_VALID -> Res.string.subscription_promo_error_invalid
    PromoCodeError.TOO_MANY_ATTEMPTS -> Res.string.subscription_promo_error_throttled
    PromoCodeError.ALREADY_SUBSCRIBED -> Res.string.subscription_promo_error_subscribed
    PromoCodeError.SIGN_IN_REQUIRED -> Res.string.subscription_promo_error_sign_in
    PromoCodeError.APP_UNVERIFIED -> Res.string.subscription_promo_error_unverified
    PromoCodeError.UNAVAILABLE -> Res.string.subscription_promo_error_offline
  }

/**
 * Renders the `PRQK-8H3M-XTVB` grouping over a raw (undashed) field value. The offset mapping shifts
 * every caret position past a dash by one, so the cursor tracks the raw text instead of jumping as
 * each separator appears.
 */
private val PromoCodeGroupingTransformation = VisualTransformation { text ->
  val raw = text.text
  val formatted = buildString {
    append(raw.take(4))
    if (raw.length > 4) append("-").append(raw.substring(4, minOf(8, raw.length)))
    if (raw.length > 8) append("-").append(raw.drop(8))
  }
  val mapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = when {
      offset <= 4 -> offset
      offset <= 8 -> offset + 1
      else -> offset + 2
    }

    override fun transformedToOriginal(offset: Int): Int = when {
      offset <= 4 -> offset
      offset <= 9 -> offset - 1
      else -> offset - 2
    }
  }
  TransformedText(AnnotatedString(formatted), mapping)
}

/** Sized to sit inside a text button without changing its height as the spinner swaps in. */
private val PROGRESS_SIZE = 18.dp
private val PROGRESS_STROKE = 2.dp
