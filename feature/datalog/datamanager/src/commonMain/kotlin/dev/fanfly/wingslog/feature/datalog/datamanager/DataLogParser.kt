package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog

/** How sure a parser is that a header is its format (design §6.2). */
enum class Confidence { NONE, POSSIBLE, DEFINITE }

/**
 * One recorder format. The only code that knows a file layout; everything above speaks
 * [ParsedDataLog]. [version] is stored on the record and bumps when the parser changes what it
 * emits, so a viewer can tell a stale catalogue from a fresh one.
 */
interface DataLogParser {
  val format: DataLogFormat
  val version: Int

  /** Judged on the first few KB of the file. Must not throw on garbage. */
  fun sniff(header: ByteArray): Confidence

  /** Throws [DataLogParseException] on a file the sniffer accepted but the body contradicts. */
  suspend fun parse(bytes: ByteArray, fileName: String): ParsedDataLog
}

class DataLogParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
