package dev.fanfly.wingslog.feature.sharing.update

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry =
  ClipEntry(ClipData.newPlainText("plain text", text))
