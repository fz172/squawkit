package dev.fanfly.wingslog.feature.export.datamanager.impl

import platform.Foundation.NSUUID

internal actual fun generateExportId(): String = NSUUID().UUIDString.lowercase()
