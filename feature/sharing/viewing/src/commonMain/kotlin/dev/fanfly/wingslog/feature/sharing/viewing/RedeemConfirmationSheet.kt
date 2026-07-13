package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.InvitePreview
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.accept
import wingslog.core.sharedassets.generated.resources.not_now
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_already_member_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_already_member_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body_role
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_body_full
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_failed_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_failed_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_needs_signin_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_needs_signin_title
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_role_technician
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_success_body
import wingslog.feature.sharing.sharedassets.generated.resources.redeem_success_title

/**
 * State of the aircraft-invite redemption surface. A non-member can't read the aircraft before
 * joining (rules deny it, and the share URL carries only id + secret), so the confirm step is
 * intentionally detail-light; the offered role is surfaced on success from the function's response.
 */
sealed interface RedeemUiState {
  data object Hidden : RedeemUiState
  /**
   * What you are about to join (#201). Resolved from the code by the server — the invitee holds no
   * aircraft id, and the rules would (rightly) refuse to resolve one for a non-member.
   *
   * [preview] is null while it is still loading, or if the lookup failed: the sheet then says less
   * rather than blocking Accept on a call that is only there to inform.
   */
  data class Confirm(val preview: InvitePreview? = null) : RedeemUiState
  /** Signed out / guest: the invite stays parked until the user signs in with a real account. */
  data object NeedsSignIn : RedeemUiState
  data object Redeeming : RedeemUiState
  data class Success(val role: ShareRole) : RedeemUiState
  data object AlreadyMember : RedeemUiState
  data class Failed(val message: String?) : RedeemUiState
}

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
      title = { Text(stringResource(Res.string.redeem_confirm_title)) },
      text = {
        // Say what they are joining and who is inviting them (#201). Until #164 this was impossible:
        // the invitee held an aircraft id the rules must refuse to resolve for a non-member, so the
        // sheet could only say "an aircraft" — and accepting meant accepting blind.
        val preview = state.preview
        Text(
          when {
            preview == null -> stringResource(Res.string.redeem_confirm_body)
            preview.hostName.isBlank() || preview.aircraftLabel.isBlank() ->
              stringResource(Res.string.redeem_confirm_body_role, redeemRoleLabel(preview.role))

            else -> stringResource(
              Res.string.redeem_confirm_body_full,
              preview.hostName,
              preview.aircraftLabel,
              redeemRoleLabel(preview.role),
            )
          },
        )
      },
      confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(CoreRes.string.accept)) } },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.not_now)) } },
    )

    RedeemUiState.NeedsSignIn -> InfoDialog(
      title = stringResource(Res.string.redeem_needs_signin_title),
      body = stringResource(Res.string.redeem_needs_signin_body),
      onDismiss = onDismiss,
    )

    RedeemUiState.Redeeming -> AlertDialog(
      onDismissRequest = {},
      confirmButton = {},
      title = { Text(stringResource(Res.string.redeem_confirm_title)) },
      text = { CircularProgressIndicator(Modifier.padding(Spacing.small)) },
    )

    // Success is a snackbar, not a dialog (see RedeemHost). Joining worked and there is nothing to
    // decide — a modal would make the user dismiss a box to get to the aircraft they just joined.
    is RedeemUiState.Success -> Unit

    RedeemUiState.AlreadyMember -> InfoDialog(
      title = stringResource(Res.string.redeem_already_member_title),
      body = stringResource(Res.string.redeem_already_member_body),
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

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.ok)) } },
  )
}

/** "a technician" / "a co-owner" — reads inside a sentence, unlike the roster's bare role chips. */
@Composable
fun redeemRoleLabel(role: ShareRole): String = when (role) {
  ShareRole.OWNER -> stringResource(Res.string.redeem_role_owner)
  ShareRole.TECHNICIAN -> stringResource(Res.string.redeem_role_technician)
}
