package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Serializes a single-thing export document into a PDF file.
 */
interface ThingPdfWriter {
  fun write(document: ThingPdfDocument): ByteArray
}
