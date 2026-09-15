package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.datalog.DataLogFormat
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog

/** How sure a parser is that a header is its format (design §6.2). */
enum class Confidence { NONE, POSSIBLE, DEFINITE }

/**
 * One recorder family. The only code that knows a file layout; everything above speaks
 * [ParsedDataLog]. [version] is stored on the record and bumps when the parser changes what it
 * emits, so a viewer can tell a stale catalogue from a fresh one.
 *
 * [formats] is a set rather than one value because two formats can be near enough to share a parser
 * — Garmin's G3X and G1000 differ only in how their two header lines are laid out. The format a
 * given file turns out to be is on the [ParsedDataLog]; this is what the parser will answer for.
 */
interface DataLogParser {
  val formats: Set<DataLogFormat>
  val version: Int

  /** Judged on the first few KB of the file. Must not throw on garbage. */
  fun sniff(header: ByteArray): Confidence

  /**
   * Every power-on session in the file, in file order.
   *
   * Most formats hold exactly one and return a single-element list. A Dynon SkyView restarts its
   * clock at each power cycle and one download holds every session since the last one, so a single
   * file can hold twenty — which is why this is a list rather than one log with a month-wide time
   * axis and a fortnight of empty space in the middle.
   *
   * [session] materialises only that session, for a viewer opening one record out of a file with
   * many. Out of range, the list comes back empty. Null builds them all, which is what an import
   * needs.
   *
   * Throws [DataLogParseException] on a file the sniffer accepted but the body contradicts.
   */
  suspend fun parse(
    bytes: ByteArray,
    fileName: String,
    session: Int? = null,
  ): List<ParsedDataLog>
}

class DataLogParseException(message: String, cause: Throwable? = null) :
  Exception(message, cause)
