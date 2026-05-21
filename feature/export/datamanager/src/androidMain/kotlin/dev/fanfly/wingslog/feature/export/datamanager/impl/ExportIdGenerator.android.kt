package dev.fanfly.wingslog.feature.export.datamanager.impl

import java.util.UUID

internal actual fun generateExportId(): String = UUID.randomUUID().toString()
