package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Serializes a single-aircraft export document into a PDF file.
 */
interface AircraftPdfWriter {
  fun write(document: AircraftPdfDocument): ByteArray
}
