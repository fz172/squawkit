package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.sharing.model.SHARE_URL_BASE
import dev.fanfly.wingslog.feature.sharing.model.formatInviteCode
import dev.fanfly.wingslog.feature.sharing.viewing.ManageAccessUiState
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.sharing.sharedassets.generated.resources.Res
import wingslog.feature.sharing.sharedassets.generated.resources.invite_code_hint
import wingslog.feature.sharing.sharedassets.generated.resources.invite_link_unavailable
import wingslog.feature.sharing.sharedassets.generated.resources.invite_qr_desc
import wingslog.feature.sharing.sharedassets.generated.resources.invite_scan_hint
import wingslog.feature.sharing.sharedassets.generated.resources.manage_access_expires_in

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
        .background(
          MaterialTheme.colorScheme.surfaceContainer,
          RoundedCornerShape(Spacing.cardCornerRadius)
        )
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
          stringResource(
            Res.string.invite_code_hint,
            LocalThingLexicon.current.thingNoun.singular,
          ),
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
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
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
        .background(
          MaterialTheme.statusColors.caution.container,
          RoundedCornerShape(Spacing.badgeCornerRadius)
        )
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
        stringResource(
          Res.string.manage_access_expires_in,
          expiresInLabel(invite.expiresAtEpochMs)
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.statusColors.caution.onContainer,
      )
    }
  }
}
