package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_role_label
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_expiry_note

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
        .background(
          MaterialTheme.colorScheme.surfaceContainer,
          RoundedCornerShape(Spacing.cardCornerRadius)
        )
        .padding(Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Icon(
        Icons.Filled.Schedule,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        stringResource(Res.string.manage_access_expiry_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
