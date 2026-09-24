package dev.fanfly.wingslog.feature.sharing.viewing.redeem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.popup.AlertDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.accept
import wingslog.core.sharedassets.generated.resources.not_now
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_already_member_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_already_member_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body_full
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body_role
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_failed_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_failed_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_needs_signin_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_needs_signin_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_success_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_success_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
fun RedeemConfirmationSheet(
  state: RedeemUiState,
  onAccept: () -> Unit,
  onDismiss: () -> Unit,
) {
  when (state) {
    RedeemUiState.Hidden -> Unit

    is RedeemUiState.Confirm -> AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(
          stringResource(
            Res.string.redeem_confirm_title,
            LexiconFormatter.sentenceCase(LocalThingLexicon.current.thingNoun),
          )
        )
      },
      text = {
        // Say what they are joining and who is inviting them (#201). Until #164 this was impossible:
        // the invitee held a thing id the rules must refuse to resolve for a non-member, so the
        // sheet could only say "an aircraft" — and accepting meant accepting blind.
        val preview = state.preview
        Text(
          when {
            preview == null -> stringResource(
              Res.string.redeem_confirm_body,
              LexiconFormatter.withArticle(LocalThingLexicon.current.thingNoun),
              LexiconFormatter.lowerFirst(LocalThingLexicon.current.collection_label),
            )

            preview.hostName.isBlank() || preview.thingLabel.isBlank() ->
              stringResource(
                Res.string.redeem_confirm_body_role,
                roleLabel(preview.role),
                LexiconFormatter.withArticle(LocalThingLexicon.current.thingNoun),
                LexiconFormatter.lowerFirst(LocalThingLexicon.current.collection_label),
              )

            else -> stringResource(
              Res.string.redeem_confirm_body_full,
              preview.hostName,
              preview.thingLabel,
              roleLabel(preview.role),
              LexiconFormatter.lowerFirst(LocalThingLexicon.current.collection_label),
            )
          },
        )
      },
      confirmButton = {
        TextButton(onClick = onAccept) {
          Text(
            stringResource(
              CoreRes.string.accept
            )
          )
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) {
          Text(
            stringResource(
              CoreRes.string.not_now
            )
          )
        }
      },
    )

    RedeemUiState.NeedsSignIn -> InfoDialog(
      title = stringResource(Res.string.redeem_needs_signin_title),
      body = stringResource(Res.string.redeem_needs_signin_body),
      onDismiss = onDismiss,
    )

    RedeemUiState.Redeeming -> AlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      title = {
        Text(
          stringResource(
            Res.string.redeem_confirm_title,
            LexiconFormatter.sentenceCase(LocalThingLexicon.current.thingNoun),
          )
        )
      },
      text = { CircularProgressIndicator(Modifier.padding(Spacing.small)) },
    )

    is RedeemUiState.Success -> InfoDialog(
      title = stringResource(Res.string.redeem_success_title),
      body = stringResource(
        Res.string.redeem_success_body,
        roleLabel(state.role),
        LocalThingLexicon.current.thingNoun.singular,
        LexiconFormatter.lowerFirst(LocalThingLexicon.current.collection_label),
      ),
      onDismiss = onDismiss,
    )

    RedeemUiState.AlreadyMember -> InfoDialog(
      title = stringResource(Res.string.redeem_already_member_title),
      body = stringResource(
        Res.string.redeem_already_member_body,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      onDismiss = onDismiss,
    )

    is RedeemUiState.Failed -> InfoDialog(
      title = stringResource(Res.string.redeem_failed_title),
      // Prefer the server's specific reason (e.g. "This invite has already been used") over the
      // generic fallback, so a failed redeem is diagnosable rather than a catch-all.
      body = state.message ?: stringResource(Res.string.redeem_failed_body),
      onDismiss = onDismiss,
    )
  }
}
