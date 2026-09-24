package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invites_hint
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invites_section
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_leave
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_member_count_plural
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_member_count_singular
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_people_section

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
          stringResource(
            Res.string.manage_access_member_count_plural,
            state.members.size
          )
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
        Text(
          stringResource(
            Res.string.manage_access_leave,
            LocalThingLexicon.current.thingNoun.singular,
          )
        )
      }
    }
  }
}
