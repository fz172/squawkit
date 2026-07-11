package dev.fanfly.wingslog.feature.sharing.viewing

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.PendingInvite
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_cancel
import wingslog.feature.sharing.sharedassets.generated.resources.invite_copy
import wingslog.feature.sharing.sharedassets.generated.resources.invite_create
import wingslog.feature.sharing.sharedassets.generated.resources.invite_link_unavailable
import wingslog.feature.sharing.sharedassets.generated.resources.invite_note
import wingslog.feature.sharing.sharedassets.generated.resources.invite_pending_title
import wingslog.feature.sharing.sharedassets.generated.resources.invite_qr_desc
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_label
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_owner
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_technician
import wingslog.feature.sharing.sharedassets.generated.resources.invite_scan_hint
import wingslog.feature.sharing.sharedassets.generated.resources.invite_share
import wingslog.feature.sharing.sharedassets.generated.resources.invite_title

/** Plain UI state for [InviteSheetScreen]; produced by the host-side InviteSheetViewModel. */
data class InviteSheetUiState(
  val selectedRole: ShareRole = ShareRole.TECHNICIAN,
  val creating: Boolean = false,
  val pendingInvites: List<PendingInvite> = emptyList(),
  /** tokenHash of the pending invite whose QR/link detail is expanded (e.g. the one just created). */
  val expandedToken: String? = null,
  val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheetScreen(
  state: InviteSheetUiState,
  onRoleSelected: (ShareRole) -> Unit,
  onCreate: () -> Unit,
  onShare: (String) -> Unit,
  onCopy: (String) -> Unit,
  onCancelInvite: (tokenHash: String) -> Unit,
  onToggleExpand: (tokenHash: String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.invite_title)) },
        navigationIcon = {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(CoreRes.string.back))
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        stringResource(Res.string.invite_role_label),
        style = MaterialTheme.typography.labelLarge,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        RoleChip(
          selected = state.selectedRole == ShareRole.TECHNICIAN,
          label = stringResource(Res.string.invite_role_technician),
          onClick = { onRoleSelected(ShareRole.TECHNICIAN) },
        )
        RoleChip(
          selected = state.selectedRole == ShareRole.OWNER,
          label = stringResource(Res.string.invite_role_owner),
          onClick = { onRoleSelected(ShareRole.OWNER) },
        )
      }

      Button(
        onClick = onCreate,
        enabled = !state.creating,
        modifier = Modifier.fillMaxWidth(),
      ) {
        if (state.creating) {
          CircularProgressIndicator(Modifier.size(18.dp))
        } else {
          Text(stringResource(Res.string.invite_create))
        }
      }

      state.error?.let { message ->
        Text(
          message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Text(
        stringResource(Res.string.invite_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (state.pendingInvites.isNotEmpty()) {
        Text(
          stringResource(Res.string.invite_pending_title),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = Spacing.small),
        )
        state.pendingInvites.forEach { invite ->
          PendingInviteCard(
            invite = invite,
            expanded = invite.tokenHash == state.expandedToken,
            onToggle = { onToggleExpand(invite.tokenHash) },
            onShare = onShare,
            onCopy = onCopy,
            onCancel = { onCancelInvite(invite.tokenHash) },
          )
        }
      }
    }
  }
}

@Composable
private fun RoleChip(selected: Boolean, label: String, onClick: () -> Unit) {
  FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** A pending invite; tapping the header reveals the QR / link / share / copy / cancel detail. */
@Composable
private fun PendingInviteCard(
  invite: PendingInvite,
  expanded: Boolean,
  onToggle: () -> Unit,
  onShare: (String) -> Unit,
  onCopy: (String) -> Unit,
  onCancel: () -> Unit,
) {
  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onToggle)
          .padding(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          roleLabel(invite.role),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        Icon(
          if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
          contentDescription = null,
        )
      }
      if (expanded) {
        HorizontalDivider()
        InviteDetail(
          url = invite.url,
          onShare = onShare,
          onCopy = onCopy,
          onCancel = onCancel,
        )
      }
    }
  }
}

@Composable
private fun InviteDetail(
  url: String?,
  onShare: (String) -> Unit,
  onCopy: (String) -> Unit,
  onCancel: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Spacing.medium),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    if (url != null) {
      Surface(color = Color.White, modifier = Modifier.size(220.dp)) {
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
      Text(
        url,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Button(onClick = { onShare(url) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.Share, contentDescription = null)
          Spacer(Modifier.width(Spacing.small))
          Text(stringResource(Res.string.invite_share))
        }
        OutlinedButton(onClick = { onCopy(url) }, modifier = Modifier.weight(1f)) {
          Icon(Icons.Filled.ContentCopy, contentDescription = null)
          Spacer(Modifier.width(Spacing.small))
          Text(stringResource(Res.string.invite_copy))
        }
      }
    } else {
      // The link can't be rebuilt on this device (secret isn't stored server-side, §3.1).
      Text(
        stringResource(Res.string.invite_link_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    TextButton(
      onClick = onCancel,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        stringResource(Res.string.invite_cancel),
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun roleLabel(role: ShareRole): String = when (role) {
  ShareRole.OWNER -> stringResource(Res.string.invite_role_owner)
  ShareRole.TECHNICIAN -> stringResource(Res.string.invite_role_technician)
}
