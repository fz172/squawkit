package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * RFC 4180 CSV renderer used by export writers.
 */
object CsvWriter {
  private const val CRLF = "\r\n"

  fun write(rows: List<List<String>>): String =
    rows.joinToString(separator = CRLF, postfix = CRLF) { row ->
      row.joinToString(separator = ",") { cell -> cell.escapeCsvCell() }
    }

  private fun String.escapeCsvCell(): String {
    val needsQuoting = any { it == ',' || it == '"' || it == '\r' || it == '\n' }
    if (!needsQuoting) return this
    return buildString {
      append('"')
      this@escapeCsvCell.forEach { char ->
        if (char == '"') append("\"\"") else append(char)
      }
      append('"')
    }
  }
}
