package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.avatar.AvatarIcon
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_label
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_revoke
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member

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
        .background(
          MaterialTheme.colorScheme.surfaceContainer,
          RoundedCornerShape(Spacing.cardCornerRadius)
        )
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AvatarIcon(
        displayName = member.displayName.ifBlank { member.uid },
        photoUri = member.photoUrl,
        size = 48.dp
      )
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
        onClick = {
          if (member.role != ShareRole.TECHNICIAN) onChangeRole(
            member.uid,
            ShareRole.TECHNICIAN
          )
        },
      )
      RoleOptionCard(
        role = ShareRole.OWNER,
        selected = member.role == ShareRole.OWNER,
        onClick = {
          if (member.role != ShareRole.OWNER) onChangeRole(
            member.uid,
            ShareRole.OWNER
          )
        },
      )
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onRemove)
        .background(
          MaterialTheme.colorScheme.errorContainer,
          RoundedCornerShape(Spacing.cardCornerRadius)
        )
        .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        Icons.Filled.PersonRemove,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer
      )
      Text(
        stringResource(Res.string.manage_access_revoke),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
      )
    }
  }
}
