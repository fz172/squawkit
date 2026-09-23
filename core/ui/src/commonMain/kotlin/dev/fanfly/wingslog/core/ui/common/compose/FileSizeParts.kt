package dev.fanfly.wingslog.core.ui.common.compose

import org.jetbrains.compose.resources.StringResource

/** A file size as its unit's string and the quantity that fills it; null quantity for the fixed "0 KB". */
internal data class FileSizeParts(val resource: StringResource, val quantity: String?)
