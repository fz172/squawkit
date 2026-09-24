package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LockPerson
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
import dev.fanfly.wingslog.feature.sharing.model.ShareMember
import dev.fanfly.wingslog.feature.sharing.viewing.roleLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_badge_you
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member

@Composable
internal fun MemberRow(
  member: ShareMember,
  clickable: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .let { if (clickable) it.clickable(onClick = onClick) else it }
      .padding(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AvatarIcon(
      displayName = member.displayName.ifBlank { member.uid },
      photoUri = member.photoUrl,
      size = 38.dp
    )
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
