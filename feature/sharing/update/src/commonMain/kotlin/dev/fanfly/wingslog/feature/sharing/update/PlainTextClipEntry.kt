package dev.fanfly.wingslog.feature.sharing.update

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry holding [text] as plain text; each platform builds [ClipEntry] its own way. */
internal expect fun plainTextClipEntry(text: String): ClipEntry
