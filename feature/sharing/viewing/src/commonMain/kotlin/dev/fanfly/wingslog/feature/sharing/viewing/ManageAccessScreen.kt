package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.technicianNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.SHARE_URL_BASE
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_cancel
import wingslog.feature.sharing.sharedassets.generated.resources.invite_copy
import wingslog.feature.sharing.sharedassets.generated.resources.invite_share
import wingslog.feature.sharing.sharedassets.generated.resources.invite_title
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_code_subtitle
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_code_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_create_invite
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_done
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_co_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_access_removed
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_code_cancelled
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_link_copied
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_toast_role_updated
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.role_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_body
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Which step of the access panel is showing. Mirrors the imported "sheet-first" design (squawkit#269). */
enum class AccessPanelView { MAIN, INVITE, CODE, MEMBER }

/**
 * A transient confirmation shown after an action (e.g. "Invite link copied"). Kept as an enum
 * rather than a plain string so the ViewModel — which has no dependency on compose resources —
 * can set it without hardcoding unlocalized text; the screen resolves it to a string when shown.
 */
enum class AccessToast { LINK_COPIED, ROLE_UPDATED, ACCESS_REMOVED, CODE_CANCELLED }

@Composable
private fun AccessToast.text(): String = when (this) {
  AccessToast.LINK_COPIED -> stringResource(Res.string.manage_access_toast_link_copied)
  AccessToast.ROLE_UPDATED -> stringResource(Res.string.manage_access_toast_role_updated)
  AccessToast.ACCESS_REMOVED -> stringResource(Res.string.manage_access_toast_access_removed)
  AccessToast.CODE_CANCELLED -> stringResource(Res.string.manage_access_toast_code_cancelled)
}

/** Plain UI state for [ManageAccessScreen]; produced by the host-side ManageAccessViewModel. */
data class ManageAccessUiState(
  val isLoading: Boolean = true,
  /** The signed-in user's role on this thing; `OWNER` may manage access, others are read-only. */
  val myRole: ShareRole? = null,
  val members: List<ShareMember> = emptyList(),
  val error: String? = null,
  /** Set once the user has left the share, so the host can pop back to the fleet. */
  val leaveSuccess: Boolean = false,
  /**
   * Set when the owner revoked this user's access while they had the screen open. Same exit as
   * [leaveSuccess] — they are no longer a member, so the roster on screen is a lie.
   */
  val accessRevoked: Boolean = false,
  /**
   * Cloud Sync is on. Sharing is a cloud feature end to end (PRD E2) — with sync off there is
   * nothing to share into and nothing to receive from, so management is disabled and explains
   * itself rather than failing on tap.
   */
  val syncEnabled: Boolean = true,
  /**
   * Hosting a share is a Pro capability (subscription gate). When off, the owner's "Create invite
   * code" action is surfaced as a promo (opens the upsell) rather than hidden; managing/leaving an
   * existing share is unaffected. `true` while the capability is off (default-open). See
   * subscription_design.html §6.
   */
  val canHostShare: Boolean = true,
  /** Which of the four steps (people → role → code → member) the panel is showing. */
  val view: AccessPanelView = AccessPanelView.MAIN,
  /** e.g. "N7245K · Cessna 172S", carried on new invites for the invitee's preview (#201). */
  val thingLabel: String = "",
  val invites: List<PendingInvite> = emptyList(),
  val selectedInviteRole: ShareRole = ShareRole.TECHNICIAN,
  val creatingInvite: Boolean = false,
  /** True while a cancel request for the active invite is in flight — disables the button so the
   *  slow round trip can't be tapped again, instead of failing silently on the second call. */
  val cancellingInvite: Boolean = false,
  /** codeId of the invite the CODE view is showing. */
  val activeInviteCodeId: String? = null,
  /** uid of the member the MEMBER view is showing. */
  val activeMemberUid: String? = null,
  val helpExpanded: Boolean = false,
  /** Transient confirmation, cleared a moment after it's shown. */
  val toast: AccessToast? = null,
) {
  /** Owners manage access; everyone else sees a read-only roster. Never while sync is off. */
  val canManage: Boolean get() = myRole == ShareRole.OWNER && syncEnabled

  /** A non-host member may leave; the host tears the share down by deleting the thing instead. */
  val canLeave: Boolean get() = syncEnabled && members.any { it.isSelf && !it.isHost }

  val activeInvite: PendingInvite? get() = invites.firstOrNull { it.codeId == activeInviteCodeId }
  val activeMember: ShareMember? get() = members.firstOrNull { it.uid == activeMemberUid }
}

@Composable
fun ManageAccessScreen(
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
  when {
    state.isLoading -> Box(
      modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      CircularProgressIndicator(Modifier.padding(Spacing.xLarge))
    }

    !state.syncEnabled -> EmptyState(
      title = stringResource(Res.string.sharing_sync_off_title),
      description = stringResource(
        Res.string.sharing_sync_off_body,
        LexiconFormatter.sentenceCasePlural(LocalThingLexicon.current.thingNoun),
      ),
      icon = Icons.Filled.CloudOff,
      modifier = modifier.fillMaxSize(),
    )

    state.members.isEmpty() -> EmptyState(
      title = stringResource(Res.string.manage_access_empty_title),
      description = stringResource(
        Res.string.manage_access_empty_desc,
        LocalThingLexicon.current.technicianNoun.singular,
        LocalThingLexicon.current.thingNoun.singular,
      ),
      icon = Icons.Filled.Group,
      modifier = modifier.fillMaxSize(),
    )

    else -> AccessPanel(
      state = state,
      onOpenInvite = onOpenInvite,
      onSelectInviteRole = onSelectInviteRole,
      onCreateInvite = onCreateInvite,
      onOpenCode = onOpenCode,
      onCancelInvite = onCancelInvite,
      onShareInvite = onShareInvite,
      onCopyInvite = onCopyInvite,
      onOpenMember = onOpenMember,
      onChangeRole = onChangeRole,
      onRevoke = onRevoke,
      onLeave = onLeave,
      onToggleHelp = onToggleHelp,
      onBackToMain = onBackToMain,
      onDismiss = onDismiss,
      onToastShown = onToastShown,
      modifier = modifier,
    )
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AccessPanel(
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

@Composable
private fun PanelHeader(state: ManageAccessUiState, onLeading: () -> Unit) {
  val (title, subtitle) = panelTitles(state)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // The dialog this panel renders in draws edge-to-edge (formDialogProperties'
      // decorFitsSystemWindows = false) only on the COMPACT full-screen presentation — the MEDIUM+
      // centered card already clears the status bar via its own outer padding, so gate this rather
      // than pushing the header down needlessly there too.
      .let { if (LocalLayoutTier.current == LayoutTier.COMPACT) it.statusBarsPadding() else it }
      .padding(
        start = Spacing.small,
        end = Spacing.large,
        top = Spacing.small,
        bottom = Spacing.small
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onLeading) {
      if (state.view == AccessPanelView.MAIN) {
        Icon(
          Icons.Filled.Close,
          contentDescription = stringResource(CoreRes.string.cancel)
        )
      } else {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = stringResource(CoreRes.string.cancel)
        )
      }
    }
    Spacer(Modifier.width(Spacing.small))
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (subtitle.isNotBlank()) {
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun panelTitles(state: ManageAccessUiState): Pair<String, String> =
  when (state.view) {
    AccessPanelView.MAIN -> stringResource(Res.string.manage_access_title) to state.thingLabel
    AccessPanelView.INVITE -> stringResource(
      Res.string.invite_title,
      LocalThingLexicon.current.thingNoun.singular,
    ) to state.thingLabel

    AccessPanelView.CODE -> {
      val invite = state.activeInvite
      val subtitle = if (invite != null) {
        stringResource(
          Res.string.manage_access_code_subtitle,
          roleLabel(invite.role, isHost = false)
        )
      } else {
        ""
      }
      stringResource(Res.string.manage_access_code_title) to subtitle
    }

    AccessPanelView.MEMBER -> {
      val member = state.activeMember
      val name =
        member?.displayName?.ifBlank { stringResource(Res.string.manage_access_unnamed_member) }
          .orEmpty()
      val subtitle = member?.let { roleLabel(it.role, it.isHost) }
        .orEmpty()
      name to subtitle
    }
  }

@Composable
private fun PanelBottomBar(
  state: ManageAccessUiState,
  onOpenInvite: () -> Unit,
  onCreateInvite: () -> Unit,
  onShareInvite: (url: String) -> Unit,
  onCopyInvite: (url: String) -> Unit,
  onCancelInvite: (codeId: String) -> Unit,
  onDone: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // See the matching comment on PanelHeader — only needed on the edge-to-edge COMPACT sheet.
      .let { if (LocalLayoutTier.current == LayoutTier.COMPACT) it.navigationBarsPadding() else it }
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    when (state.view) {
      AccessPanelView.MAIN -> if (state.canManage) {
        PrimaryActionButton(
          label = stringResource(Res.string.manage_access_create_invite),
          onClick = onOpenInvite,
        )
      }

      AccessPanelView.INVITE -> PrimaryActionButton(
        label = stringResource(Res.string.manage_access_create_invite),
        loading = state.creatingInvite,
        onClick = onCreateInvite,
      )

      AccessPanelView.CODE -> {
        val invite = state.activeInvite
        val code = invite?.code
        if (code != null) {
          val url = "$SHARE_URL_BASE#$code"
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Button(
              onClick = { onShareInvite(url) },
              modifier = Modifier.weight(1f)
                .height(Spacing.buttonHeight),
            ) {
              Icon(Icons.Filled.IosShare, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(Res.string.invite_share))
            }
            OutlinedButton(
              onClick = { onCopyInvite(url) },
              modifier = Modifier.weight(1f)
                .height(Spacing.buttonHeight),
            ) {
              Icon(Icons.Filled.ContentCopy, contentDescription = null)
              Spacer(Modifier.width(Spacing.small))
              Text(stringResource(Res.string.invite_copy))
            }
          }
        }
        invite?.let {
          TextButton(
            onClick = { onCancelInvite(it.codeId) },
            enabled = !state.cancellingInvite,
            modifier = Modifier.fillMaxWidth(),
          ) {
            if (state.cancellingInvite) {
              CircularProgressIndicator(
                Modifier.padding(2.dp)
                  .height(20.dp)
                  .width(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error,
              )
            } else {
              Text(
                stringResource(Res.string.invite_cancel),
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }

      AccessPanelView.MEMBER -> PrimaryActionButton(
        label = stringResource(Res.string.manage_access_done),
        onClick = onDone,
      )
    }
  }
}

@Composable
private fun PrimaryActionButton(
  label: String,
  onClick: () -> Unit,
  loading: Boolean = false
) {
  Button(
    onClick = onClick,
    enabled = !loading,
    modifier = Modifier.fillMaxWidth()
      .height(Spacing.buttonHeight),
  ) {
    if (loading) {
      CircularProgressIndicator(
        Modifier.padding(2.dp)
          .height(20.dp)
          .width(20.dp), strokeWidth = 2.dp
      )
    } else {
      Text(label)
    }
  }
}

/**
 * The hosting owner is *the* owner; anyone else holding the owner role is a co-owner. Same wire
 * role — the distinction is who the thing belongs to, and calling both "Owner" hid that.
 */
@Composable
internal fun roleLabel(role: ShareRole, isHost: Boolean): String = when (role) {
  ShareRole.OWNER ->
    if (isHost) stringResource(Res.string.manage_access_role_owner)
    else stringResource(Res.string.manage_access_role_co_owner)

  ShareRole.TECHNICIAN -> LexiconFormatter.titleCase(LocalThingLexicon.current.technicianNoun)
}

/** A destructive confirmation: the action is named on the button, so the user reads what they are doing. */
@Composable
internal fun ConfirmDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel, color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) }
    },
  )
}
