package dev.fanfly.wingslog.feature.attachment.datamanager

/** Where a file lives in OPFS: its parent `FileSystemDirectoryHandle` and its own name. */
internal data class OpfsFileLocation(val directory: dynamic, val name: String)
