package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.AvatarIcon
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.SHARE_URL_BASE
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.model.formatInviteCode
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_code_hint
import wingslog.feature.sharing.sharedassets.generated.resources.invite_link_unavailable
import wingslog.feature.sharing.sharedassets.generated.resources.invite_qr_desc
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_label
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_technician
import wingslog.feature.sharing.sharedassets.generated.resources.invite_scan_hint
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_badge_you
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_expires_in
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_expiry_note
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_help_footer
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_help_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invite_meta
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invites_hint
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invites_section
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_leave
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_member_count_plural
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_member_count_singular
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_people_section
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_aircraft_details
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_manage_access
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_squawks_tasks
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_perm_work_logs
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_revoke
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_co_owner_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_role_technician_desc
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_solo_body
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_solo_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member

@Composable
internal fun MainView(
  state: ManageAccessUiState,
  onOpenCode: (String) -> Unit,
  onOpenMember: (String) -> Unit,
  onToggleHelp: () -> Unit,
  onLeave: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.large, vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      SectionHeader(
        title = stringResource(Res.string.manage_access_people_section),
        trailing = if (state.members.size == 1) {
          stringResource(Res.string.manage_access_member_count_singular)
        } else {
          stringResource(Res.string.manage_access_member_count_plural, state.members.size)
        },
      )
      GroupedList {
        state.members.forEachIndexed { index, member ->
          MemberRow(
            member = member,
            clickable = state.canManage && !member.isHost && !member.isSelf,
            onClick = { onOpenMember(member.uid) },
          )
          if (index < state.members.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
      }
    }

    if (state.members.size == 1 && state.invites.isEmpty()) {
      SoloEmptyCallout()
    }

    if (state.canManage && state.invites.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        SectionHeader(
          title = stringResource(Res.string.manage_access_invites_section),
          trailing = state.invites.size.toString(),
        )
        GroupedList {
          state.invites.forEachIndexed { index, invite ->
            InviteRow(invite = invite, onClick = { onOpenCode(invite.codeId) })
            if (index < state.invites.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        Text(
          stringResource(Res.string.manage_access_invites_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    PermissionsCard(expanded = state.helpExpanded, onToggle = onToggleHelp)

    if (state.canLeave) {
      OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(Spacing.small))
        Text(stringResource(Res.string.manage_access_leave))
      }
    }
  }
}

@Composable
internal fun InviteView(
  state: ManageAccessUiState,
  onSelectInviteRole: (ShareRole) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.large, vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      SectionHeader(title = stringResource(Res.string.invite_role_label))
      RoleOptionCard(
        role = ShareRole.TECHNICIAN,
        selected = state.selectedInviteRole == ShareRole.TECHNICIAN,
        onClick = { onSelectInviteRole(ShareRole.TECHNICIAN) },
      )
      RoleOptionCard(
        role = ShareRole.OWNER,
        selected = state.selectedInviteRole == ShareRole.OWNER,
        onClick = { onSelectInviteRole(ShareRole.OWNER) },
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Spacing.cardCornerRadius))
        .padding(Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        stringResource(Res.string.manage_access_expiry_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun CodeView(state: ManageAccessUiState) {
  val invite = state.activeInvite
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.large, vertical = Spacing.small),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    if (invite == null) return@Column
    // Captured locally: a `val` on a class from another Gradle module isn't smart-cast-stable, so
    // the null check above doesn't carry through to `invite.code` below without this.
    val code = invite.code

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Spacing.cardCornerRadius))
        .padding(Spacing.extraLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      if (code != null) {
        Text(
          formatInviteCode(code),
          style = MaterialTheme.typography.displaySmall,
          fontFamily = FontFamily.Monospace,
          letterSpacing = 4.sp,
        )
        Text(
          stringResource(Res.string.invite_code_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      } else {
        Text(
          stringResource(Res.string.invite_link_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }

    if (code != null) {
      val url = "$SHARE_URL_BASE#$code"
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Surface(color = Color.White, modifier = Modifier.size(200.dp)) {
          Image(
            painter = rememberQrCodePainter(url),
            contentDescription = stringResource(Res.string.invite_qr_desc),
            modifier = Modifier.padding(Spacing.small),
          )
        }
        Text(
          stringResource(Res.string.invite_scan_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Row(
      modifier = Modifier
        .background(MaterialTheme.statusColors.caution.container, RoundedCornerShape(Spacing.badgeCornerRadius))
        .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Icon(
        Icons.Filled.HourglassTop,
        contentDescription = null,
        tint = MaterialTheme.statusColors.caution.onContainer,
        modifier = Modifier.size(16.dp),
      )
      Text(
        stringResource(Res.string.manage_access_expires_in, expiresInLabel(invite.expiresAtEpochMs)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.statusColors.caution.onContainer,
      )
    }
  }
}

@Composable
internal fun MemberView(
  state: ManageAccessUiState,
  onChangeRole: (uid: String, role: ShareRole) -> Unit,
  onRemove: () -> Unit,
) {
  val member = state.activeMember ?: return
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Spacing.large, vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Spacing.cardCornerRadius))
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AvatarIcon(displayName = member.displayName.ifBlank { member.uid }, photoUri = member.photoUrl, size = 48.dp)
      Spacer(Modifier.width(Spacing.medium))
      Text(
        member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      SectionHeader(title = stringResource(Res.string.invite_role_label))
      RoleOptionCard(
        role = ShareRole.TECHNICIAN,
        selected = member.role == ShareRole.TECHNICIAN,
        onClick = { if (member.role != ShareRole.TECHNICIAN) onChangeRole(member.uid, ShareRole.TECHNICIAN) },
      )
      RoleOptionCard(
        role = ShareRole.OWNER,
        selected = member.role == ShareRole.OWNER,
        onClick = { if (member.role != ShareRole.OWNER) onChangeRole(member.uid, ShareRole.OWNER) },
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onRemove)
        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(Spacing.cardCornerRadius))
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(Icons.Filled.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
      Text(
        stringResource(Res.string.manage_access_revoke),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}

@Composable
private fun RoleOptionCard(role: ShareRole, selected: Boolean, onClick: () -> Unit) {
  val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
  val backgroundColor = if (selected) {
    MaterialTheme.colorScheme.primaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val (name, desc, icon) = when (role) {
    ShareRole.TECHNICIAN -> Triple(
      stringResource(Res.string.invite_role_technician),
      stringResource(Res.string.manage_access_role_technician_desc),
      Icons.Filled.Construction,
    )

    ShareRole.OWNER -> Triple(
      stringResource(Res.string.invite_role_owner),
      stringResource(Res.string.manage_access_role_co_owner_desc),
      Icons.Filled.Flight,
    )
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .background(backgroundColor, RoundedCornerShape(Spacing.cardCornerRadius))
      .border(Spacing.hairline, borderColor, RoundedCornerShape(Spacing.cardCornerRadius))
      .padding(Spacing.medium),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
      Text(name, style = MaterialTheme.typography.titleSmall)
      Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun MemberRow(member: ShareMember, clickable: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .let { if (clickable) it.clickable(onClick = onClick) else it }
      .padding(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AvatarIcon(displayName = member.displayName.ifBlank { member.uid }, photoUri = member.photoUrl, size = 38.dp)
    Spacer(Modifier.width(Spacing.medium))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          member.displayName.ifBlank { stringResource(Res.string.manage_access_unnamed_member) },
          style = MaterialTheme.typography.titleSmall,
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
    if (member.isHost && !member.isSelf) {
      Icon(
        Icons.Filled.LockPerson,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
      )
    } else if (clickable) {
      Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun InviteRow(invite: PendingInvite, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(38.dp)
        .background(MaterialTheme.statusColors.caution.container, RoundedCornerShape(19.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.statusColors.caution.accent)
    }
    Spacer(Modifier.width(Spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        formatInviteCode(invite.code ?: invite.codeId.take(8)),
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
      )
      Text(
        stringResource(Res.string.manage_access_invite_meta, roleLabel(invite.role, isHost = false), expiresInLabel(invite.expiresAtEpochMs)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SoloEmptyCallout() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Spacing.cardCornerRadius))
      .padding(Spacing.large),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    Column {
      Text(
        stringResource(Res.string.manage_access_solo_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        stringResource(Res.string.manage_access_solo_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
  }
}

@Composable
private fun PermissionsCard(expanded: Boolean, onToggle: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(Spacing.cardCornerRadius)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onToggle)
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        stringResource(Res.string.manage_access_help_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
      )
      Icon(
        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (expanded) {
      Column(modifier = Modifier.padding(start = Spacing.medium, end = Spacing.medium, bottom = Spacing.medium)) {
        val rows = listOf(
          Triple(stringResource(Res.string.manage_access_perm_squawks_tasks), true, true),
          Triple(stringResource(Res.string.manage_access_perm_work_logs), true, true),
          Triple(stringResource(Res.string.manage_access_perm_aircraft_details), false, true),
          Triple(stringResource(Res.string.manage_access_perm_manage_access), false, true),
        )
        Row(Modifier.fillMaxWidth()) {
          Spacer(Modifier.weight(1f))
          Text(
            stringResource(Res.string.invite_role_technician),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
          )
          Text(
            stringResource(Res.string.invite_role_owner),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
          )
        }
        rows.forEach { (label, tech, owner) ->
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            PermCell(tech, Modifier.width(64.dp))
            PermCell(owner, Modifier.width(64.dp))
          }
        }
        Text(
          stringResource(Res.string.manage_access_help_footer),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = Spacing.small),
        )
      }
    }
  }
}

@Composable
private fun PermCell(granted: Boolean, modifier: Modifier = Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    Icon(
      if (granted) Icons.Filled.Check else Icons.Filled.Remove,
      contentDescription = null,
      tint = if (granted) {
        MaterialTheme.statusColors.positive.accent
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier.size(18.dp),
    )
  }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (trailing != null) {
      Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun GroupedList(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(content = content)
  }
}

@Composable
private fun Pill(text: String) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

/** "23h 41m" / "6d" style countdown to [expiresAtEpochMs]; "Expired" once past. */
private fun expiresInLabel(expiresAtEpochMs: Long): String {
  val remainingMs = expiresAtEpochMs - Clock.System.now().toEpochMilliseconds()
  if (remainingMs <= 0) return "0m"
  val totalMinutes = remainingMs / 60_000
  val days = totalMinutes / (24 * 60)
  val hours = (totalMinutes % (24 * 60)) / 60
  val minutes = totalMinutes % 60
  return when {
    days > 0 -> "${days}d ${hours}h"
    hours > 0 -> "${hours}h ${minutes}m"
    else -> "${minutes}m"
  }
}
