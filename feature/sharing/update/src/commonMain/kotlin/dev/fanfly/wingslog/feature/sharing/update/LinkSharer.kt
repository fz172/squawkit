package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.runtime.Composable

/** Opens the platform share sheet for a plain-text link (the aircraft-share invite URL). */
interface LinkSharer {
  /** Returns true when the platform accepted the share request. */
  fun shareLink(url: String, chooserTitle: String): Boolean
}

/** Remembers the platform [LinkSharer] used by the invite sheet's Share action. */
@Composable
expect fun rememberLinkSharer(): LinkSharer
