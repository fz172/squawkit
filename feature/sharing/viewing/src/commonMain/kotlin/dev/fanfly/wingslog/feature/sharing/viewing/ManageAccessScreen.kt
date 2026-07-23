package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.leave_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_badge_you
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_empty_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invite
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_leave
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_make_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_make_technician
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_member_actions
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_revoke
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_co_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_technician
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_action
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_body
import wingslog.feature.sharing.sharedassets.generated.resources.revoke_confirm_title
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_body
import wingslog.feature.sharing.sharedassets.generated.resources.sharing_sync_off_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Plain UI state for [ManageAccessScreen]; produced by the host-side ManageAccessViewModel. */
data class ManageAccessUiState(
  val isLoading: Boolean = true,
  /** The signed-in user's role on this aircraft; `OWNER` may manage access, others are read-only. */
  val myRole: ShareRole? = null,
  val members: List<ShareMember> = emptyList(),
  val error: String? = null,
  /** Set once the user has left the share, so the host can pop back to the fleet. */
  val leaveSuccess: Boolean = false,
  /**
   * Set when the owner revoked this user's access while they had the screen open. Same exit as
   * [leaveSuccess] — they are no longer a member, so the roster in front of them is a lie.
   */
  val accessRevoked: Boolean = false,
  /**
   * Cloud Sync is on. Sharing is a cloud feature end to end (PRD E2) — with sync off there is
   * nothing to share into and nothing to receive from, so management is disabled and explains
   * itself rather than failing on tap.
   */
  val syncEnabled: Boolean = true,
  /**
   * Hosting a share is a Pro capability (subscription gate). When off, the owner's Invite action is
   * surfaced as a promo (opens the upsell) rather than hidden; managing/leaving an existing share is
   * unaffected. `true` while the capability is off (default-open). See subscription_design.html §6.
   */
  val canHostShare: Boolean = true,
) {
  /** Owners manage access; everyone else sees a read-only roster. Never while sync is off. */
  val canManage: Boolean get() = myRole == ShareRole.OWNER && syncEnabled

  /** A non-host member may leave; the host tears the share down by deleting the aircraft instead. */
  val canLeave: Boolean get() = syncEnabled && members.any { it.isSelf && !it.isHost }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccessScreen(
  state: ManageAccessUiState,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRevoke: (uid: String) -> Unit,
  onLeave: () -> Unit,
  onInvite: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      ConstrainedTopBar {
        TopAppBar(
          title = { Text(stringResource(Res.string.manage_access_title)) },
          navigationIcon = {
            IconButton(onClick = onBack) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(CoreRes.string.back),
              )
            }
          },
        )
      }
    },
    floatingActionButton = {
      // Only owners can invite; others see a read-only roster.
      if (state.canManage) {
        ExtendedFloatingActionButton(
          onClick = onInvite,
          icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
          text = { Text(stringResource(Res.string.manage_access_invite)) },
        )
      }
    },
  ) { padding ->
    // Revoking destroys work: the member's unsynced offline edits go with their access (PRD D3).
    // Neither of these is undoable, and neither used to ask.
    var revoking by remember { mutableStateOf<ShareMember?>(null) }
    var leaving by remember { mutableStateOf(false) }

    revoking?.let { member ->
      ConfirmDialog(
        title = stringResource(
          Res.string.revoke_confirm_title,
          member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
        ),
        body = stringResource(Res.string.revoke_confirm_body),
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
        title = stringResource(Res.string.leave_confirm_title),
        body = stringResource(Res.string.leave_confirm_body),
        confirmLabel = stringResource(Res.string.leave_confirm_action),
        onConfirm = {
          onLeave()
          leaving = false
        },
        onDismiss = { leaving = false },
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      when {
        state.isLoading -> CircularProgressIndicator(Modifier.padding(Spacing.xLarge))

        !state.syncEnabled -> EmptyState(
          title = stringResource(Res.string.sharing_sync_off_title),
          description = stringResource(Res.string.sharing_sync_off_body),
          icon = Icons.Filled.CloudOff,
          modifier = Modifier.fillMaxSize(),
        )

        state.members.isEmpty() -> EmptyState(
          title = stringResource(Res.string.manage_access_empty_title),
          description = stringResource(Res.string.manage_access_empty_desc),
          icon = Icons.Filled.Group,
          modifier = Modifier.fillMaxSize(),
        )

        else -> LazyColumn(
          modifier = Modifier.constrainedContentWidth(ContentWidth.Reading),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(
            Spacing.large
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          items(state.members, key = { it.uid }) { member ->
            MemberCard(
              member = member,
              canManage = state.canManage,
              onChangeRole = onChangeRole,
              onRevoke = { uid ->
                revoking = state.members.firstOrNull { it.uid == uid }
              },
            )
          }
          if (state.canLeave) {
            item {
              OutlinedButton(
                onClick = { leaving = true },
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = Spacing.small),
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.Logout,
                  contentDescription = null
                )
                Spacer(Modifier.width(Spacing.small))
                Text(stringResource(Res.string.manage_access_leave))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MemberCard(
  member: ShareMember,
  canManage: Boolean,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRevoke: (uid: String) -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AvatarIcon(
        displayName = member.displayName.ifBlank { member.uid },
        photoUri = member.photoUrl,
        size = 40.dp,
      )
      Spacer(Modifier.width(Spacing.medium))
      androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            // A uid is not a name. A member doc can be missing (never published, or lost), and
            // showing the raw id reads like corruption — say what we actually know instead.
            member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (member.isSelf) {
            Spacer(Modifier.width(Spacing.small))
            Pill(stringResource(Res.string.manage_access_badge_you))
          }
        }
        Text(
          roleLabel(member.role, member.isHost),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // Owners may change role / revoke on other members — never the host, never themselves
      // (the current user leaves via the Leave action instead).
      if (canManage && !member.isHost && !member.isSelf) {
        MemberActions(
          member = member,
          onChangeRole = onChangeRole,
          onRevoke = onRevoke
        )
      }
    }
  }
}

@Composable
private fun MemberActions(
  member: ShareMember,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRevoke: (uid: String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { expanded = true }) {
      Icon(
        Icons.Filled.MoreVert,
        contentDescription = stringResource(Res.string.manage_access_member_actions),
      )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      if (member.role != ShareRole.OWNER) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.manage_access_make_owner)) },
          onClick = {
            expanded = false
            onChangeRole(member.uid, ShareRole.OWNER)
          },
        )
      }
      if (member.role != ShareRole.TECHNICIAN) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.manage_access_make_technician)) },
          onClick = {
            expanded = false
            onChangeRole(member.uid, ShareRole.TECHNICIAN)
          },
        )
      }
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.manage_access_revoke)) },
        onClick = {
          expanded = false
          onRevoke(member.uid)
        },
      )
    }
  }
}

@Composable
private fun Pill(text: String) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    shape = RoundedCornerShape(4.dp),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

/**
 * The hosting owner is *the* owner; anyone else holding the owner role is a co-owner. Same wire
 * role — the distinction is who the aircraft belongs to, and calling both "Owner" hid that.
 */
@Composable
private fun roleLabel(role: ShareRole, isHost: Boolean): String = when (role) {
  ShareRole.OWNER ->
    if (isHost) stringResource(Res.string.manage_access_role_owner)
    else stringResource(Res.string.manage_access_role_co_owner)

  ShareRole.TECHNICIAN -> stringResource(Res.string.manage_access_role_technician)
}

/** A destructive confirmation: the action is named on the button, so the user reads what they are doing. */
@Composable
private fun ConfirmDialog(
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
