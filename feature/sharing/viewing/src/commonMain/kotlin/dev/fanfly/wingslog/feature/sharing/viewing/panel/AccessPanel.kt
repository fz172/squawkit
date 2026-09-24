package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.AccessPanelView
import dev.fanfly.wingslog.feature.sharing.viewing.ConfirmDialog
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import dev.fanfly.wingslog.feature.sharing.viewing.roleLabel
import dev.fanfly.wingslog.feature.sharing.viewing.text
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_title

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AccessPanel(
  state: ManageAccessUiState,
  onOpenInvite: () -> Unit,
  onSelectInviteRole: (ShareRole) -> Unit,
  onCreateInvite: () -> Unit,
  onOpenCode: (codeId: String) -> Unit,
  onCancelInvite: (codeId: String) -> Unit,
  onShareInvite: (url: String) -> Unit,
  onCopyInvite: (url: String) -> Unit,
  onOpenMember: (uid: String) -> Unit,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRevoke: (uid: String) -> Unit,
  onLeave: () -> Unit,
  onToggleHelp: () -> Unit,
  onBackToMain: () -> Unit,
  onDismiss: () -> Unit,
  onToastShown: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Revoking/leaving destroy work: unsynced offline edits go with the access (PRD D3). Neither is
  // undoable, and neither used to ask.
  var revoking by remember { mutableStateOf<ShareMember?>(null) }
  var leaving by remember { mutableStateOf(false) }
  // A role change is a real permission grant/revoke (co-owner can edit thing details and manage
  // access), and it takes effect immediately — so it gets the same "ask first" treatment. The member
  // is captured with the tap (not re-looked-up from state.activeMember at render time), same as
  // [revoking] above, so the dialog can't dangle if the roster changes underneath it.
  var pendingRoleChange by remember {
    mutableStateOf<Pair<ShareMember, ShareRole>?>(
      null
    )
  }

  // The system/gesture back press otherwise dismisses the whole dialog (it's the Dialog's own
  // onDismissRequest) regardless of which of the four steps is showing. From anywhere but MAIN,
  // back should step back to the roster first, same as the header's back arrow.
  BackHandler(enabled = state.view != AccessPanelView.MAIN) { onBackToMain() }

  val snackbarHostState = remember { SnackbarHostState() }
  val toastText = state.toast?.text()
  LaunchedEffect(state.toast) {
    val message = toastText ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    onToastShown()
  }

  revoking?.let { member ->
    ConfirmDialog(
      title = stringResource(
        Res.string.revoke_confirm_title,
        member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
      ),
      body = stringResource(
        Res.string.revoke_confirm_body,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      confirmLabel = stringResource(Res.string.revoke_confirm_action),
      onConfirm = {
        onRevoke(member.uid)
        revoking = null
      },
      onDismiss = { revoking = null },
    )
  }

  if (leaving) {
    ConfirmDialog(
      title = stringResource(
        Res.string.leave_confirm_title,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      body = stringResource(
        Res.string.leave_confirm_body,
        LexiconFormatter.lowerFirst(LocalThingLexicon.current.collection_label),
      ),
      confirmLabel = stringResource(Res.string.leave_confirm_action),
      onConfirm = {
        onLeave()
        leaving = false
      },
      onDismiss = { leaving = false },
    )
  }

  pendingRoleChange?.let { (member, role) ->
    ConfirmDialog(
      title = stringResource(
        Res.string.role_confirm_title,
        member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
        roleLabel(role, isHost = false),
      ),
      body = stringResource(
        Res.string.role_confirm_body,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      confirmLabel = stringResource(Res.string.role_confirm_action),
      onConfirm = {
        onChangeRole(member.uid, role)
        pendingRoleChange = null
      },
      onDismiss = { pendingRoleChange = null },
    )
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      PanelHeader(
        state = state,
        onLeading = if (state.view == AccessPanelView.MAIN) onDismiss else onBackToMain,
      )
    },
    bottomBar = {
      PanelBottomBar(
        state = state,
        onOpenInvite = onOpenInvite,
        onCreateInvite = onCreateInvite,
        onShareInvite = onShareInvite,
        onCopyInvite = onCopyInvite,
        onCancelInvite = onCancelInvite,
        onDone = onBackToMain,
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Box(Modifier.padding(padding)) {
      when (state.view) {
        AccessPanelView.MAIN -> MainView(
          state = state,
          onOpenCode = onOpenCode,
          onOpenMember = onOpenMember,
          onToggleHelp = onToggleHelp,
          onLeave = { leaving = true },
        )

        AccessPanelView.INVITE -> InviteView(
          state = state,
          onSelectInviteRole = onSelectInviteRole,
        )

        AccessPanelView.CODE -> CodeView(state = state)

        AccessPanelView.MEMBER -> MemberView(
          state = state,
          onChangeRole = { uid, role ->
            state.activeMember?.takeIf { it.uid == uid }
              ?.let { member ->
                pendingRoleChange = member to role
              }
          },
          onRemove = { state.activeMember?.let { revoking = it } },
        )
      }
    }
  }
}
