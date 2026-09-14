package dev.fanfly.wingslog.feature.datalog.model

import dev.fanfly.wingslog.id.DataLogId

/** One import, as a flow of states (design §6.1). Terminal: [Done], [NeedsConfirmation], [Failed]. */
sealed class ImportProgress {
  data object Reading : ImportProgress()
  data class Parsing(val rowsSoFar: Int) : ImportProgress()
  data object Storing : ImportProgress()
  data class Done(val id: DataLogId) : ImportProgress()

  /** The same recorder and start as [existing]; the caller re-runs with confirmation to keep both. */
  data class NeedsConfirmation(val existing: DataLogId) : ImportProgress()
  data class Failed(val reason: ImportFailure) : ImportProgress()
}

/** Maps one-to-one onto the `data_log_import_failed` analytics values. */
enum class ImportFailure {
  /** The file could not be opened or read. */
  UNREADABLE,
  /** No parser recognised the header. */
  UNRECOGNIZED,
  /** The same file (by content hash) is already on this Thing. */
  DUPLICATE,
  PARSE_ERROR,
}
