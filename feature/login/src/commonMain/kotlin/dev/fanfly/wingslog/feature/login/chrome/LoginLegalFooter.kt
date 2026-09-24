package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.feature.login.privacyPolicyUrl
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.feature.login.generated.resources.support_link

/** The disclaimer plus the Terms & Privacy / Support links, centred under the card. */
@Composable
internal fun LoginLegalFooter() {
  val uriHandler = LocalUriHandler.current
  val appCapability: AppCapability = koinInject()

  Text(
    text = stringResource(Res.string.legal_disclaimer),
    style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
  )

  Spacer(Modifier.height(14.dp))

  Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
    Text(
      text = stringResource(Res.string.privacy_notice),
      style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) },
    )
    appCapability.supportUrl?.let { support ->
      Text(
        text = stringResource(Res.string.support_link),
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(support) },
      )
    }
  }
}
