package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.attachment.sharedassets.generated.resources.add_link
import wingslog.feature.attachment.sharedassets.generated.resources.invalid_url
import wingslog.feature.attachment.sharedassets.generated.resources.link_name
import wingslog.feature.attachment.sharedassets.generated.resources.link_url
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
internal fun LinkForm(
  onAddLink: (url: String, name: String) -> Unit,
  onCancel: () -> Unit,
) {
  var linkUrl by remember { mutableStateOf("") }
  var linkName by remember { mutableStateOf("") }
  var urlError by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    FormTextField(
      value = linkUrl,
      onValueChange = { linkUrl = it; urlError = false },
      label = stringResource(AttachRes.string.link_url),
      isError = urlError,
      supportingText = if (urlError) stringResource(AttachRes.string.invalid_url) else null,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    FormTextField(
      value = linkName,
      onValueChange = { linkName = it },
      label = stringResource(AttachRes.string.link_name),
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
    )
    Row(
      horizontalArrangement = Arrangement.End,
      modifier = Modifier.fillMaxWidth(),
    ) {
      TextButton(onClick = onCancel) {
        Text(stringResource(CoreRes.string.cancel))
      }
      FilledTonalButton(onClick = {
        val trimmed = linkUrl.trim()
        if (!isValidUrl(trimmed)) {
          urlError = true
        } else {
          val normalized =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
              trimmed
            } else {
              "https://$trimmed"
            }
          onAddLink(
            normalized,
            linkName.trim()
              .ifBlank { normalized.extractDomain() })
        }
      }) {
        Text(stringResource(AttachRes.string.add_link))
      }
    }
  }
}

private fun String.extractDomain(): String {
  val withoutScheme = if (contains("://")) substringAfter("://") else this
  val hostAndPort = withoutScheme.substringBefore("/")
    .substringBefore("?")
    .substringBefore("#")
  val host = hostAndPort.substringBefore(":")
  return if (host.startsWith("www.")) host.drop(4) else host
}

private fun isValidUrl(url: String): Boolean {
  if (url.isBlank()) return false
  val lower = url.lowercase()
    .trim()
  if (lower.startsWith("ftp://") || lower.startsWith("file://") || lower.startsWith(
      "mailto:"
    )
  ) return false
  val normalized =
    if (lower.startsWith("http://") || lower.startsWith("https://")) lower else "https://$lower"
  val withoutProtocol = normalized.substringAfter("://")
  val dotIndex = withoutProtocol.indexOf('.')
  return dotIndex > 0 && dotIndex < withoutProtocol.lastIndex && !withoutProtocol.contains(
    ' '
  )
}
