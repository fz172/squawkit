package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.ui.platform.ClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
