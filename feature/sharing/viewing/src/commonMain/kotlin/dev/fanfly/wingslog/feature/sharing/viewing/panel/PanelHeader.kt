package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.sharing.viewing.AccessPanelView
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import dev.fanfly.wingslog.feature.sharing.viewing.roleLabel
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_code_subtitle
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_code_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_title
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_unnamed_member
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun PanelHeader(state: ManageAccessUiState, onLeading: () -> Unit) {
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
private fun panelTitles(state: ManageAccessUiState): PanelTitles =
  when (state.view) {
    AccessPanelView.MAIN -> PanelTitles(stringResource(Res.string.manage_access_title), state.thingLabel)
    AccessPanelView.INVITE -> PanelTitles(
      stringResource(Res.string.invite_title, LocalThingLexicon.current.thingNoun.singular),
      state.thingLabel,
    )

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
      PanelTitles(stringResource(Res.string.manage_access_code_title), subtitle)
    }

    AccessPanelView.MEMBER -> {
      val member = state.activeMember
      val name =
        member?.displayName?.ifBlank { stringResource(Res.string.manage_access_unnamed_member) }
          .orEmpty()
      val subtitle = member?.let { roleLabel(it.role, it.isHost) }
        .orEmpty()
      PanelTitles(name, subtitle)
    }
  }
