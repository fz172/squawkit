package dev.fanfly.wingslog.feature.datalog.datamanager

/**
 * Picks the parser for a file from its first [SNIFF_BYTES] bytes: the highest confidence wins,
 * `DEFINITE` before `POSSIBLE`, and no parser at `NONE` means the file is unrecognised.
 */
class HeaderSniffer(private val parsers: List<DataLogParser>) {

  fun sniff(bytes: ByteArray): DataLogParser? {
    val header = if (bytes.size > SNIFF_BYTES) bytes.copyOf(SNIFF_BYTES) else bytes
    return parsers
      .map { it to it.sniff(header) }
      .filter { (_, confidence) -> confidence != Confidence.NONE }
      .maxByOrNull { (_, confidence) -> confidence }
      ?.first
  }

  companion object {
    const val SNIFF_BYTES = 4096
  }
}
