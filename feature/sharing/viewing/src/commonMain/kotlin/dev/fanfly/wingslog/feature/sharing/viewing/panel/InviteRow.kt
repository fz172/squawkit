package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.formatInviteCode
import dev.fanfly.wingslog.feature.sharing.viewing.roleLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_invite_meta

@Composable
internal fun InviteRow(invite: PendingInvite, onClick: () -> Unit) {
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
        .background(
          MaterialTheme.statusColors.caution.container,
          RoundedCornerShape(19.dp)
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.QrCode2,
        contentDescription = null,
        tint = MaterialTheme.statusColors.caution.accent
      )
    }
    Spacer(Modifier.width(Spacing.medium))
    Column(Modifier.weight(1f)) {
      Text(
        formatInviteCode(invite.code ?: invite.codeId.take(8)),
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
      )
      Text(
        stringResource(
          Res.string.manage_access_invite_meta,
          roleLabel(invite.role, isHost = false),
          expiresInLabel(invite.expiresAtEpochMs)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      Icons.Filled.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
