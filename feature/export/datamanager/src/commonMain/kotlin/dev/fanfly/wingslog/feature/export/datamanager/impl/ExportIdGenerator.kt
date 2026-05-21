package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Generates stable identifiers for newly created export records.
 */
internal expect fun generateExportId(): String
