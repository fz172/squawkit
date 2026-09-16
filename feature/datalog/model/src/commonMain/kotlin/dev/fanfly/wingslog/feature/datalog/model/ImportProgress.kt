package dev.fanfly.wingslog.feature.datalog.model

import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId

/** One import, as a flow of states (design §6.1). Terminal: [Done], [NeedsConfirmation], [Failed]. */
sealed class ImportProgress {
  data object Reading : ImportProgress()
  data class Parsing(val rowsSoFar: Int) : ImportProgress()
  data object Storing : ImportProgress()

  /**
   * [id] is the first session stored. [sessionCount] is how many the file held — one for a Garmin,
   * as many as the pilot flew between downloads for a SkyView.
   */
  data class Done(val id: DataLogId, val sessionCount: Int = 1) :
    ImportProgress()

  /** The same recorder and start as [existing]; the caller re-runs with confirmation to keep both. */
  data class NeedsConfirmation(val existing: DataLogId) : ImportProgress()

  /**
   * The recorded identity is not this Thing's but is [candidate]'s (PRD R12); the caller re-runs
   * against the candidate to file it there, or with `keepIdentity` to keep it here.
   */
  data class OtherThing(val candidate: ThingId, val candidateName: String) :
    ImportProgress()

  data class Failed(val reason: ImportFailure) : ImportProgress()
}

/** Maps one-to-one onto the `data_log_import_failed` analytics values. */
enum class ImportFailure {
  /** The file could not be opened or read. */
  UNREADABLE,

  /** No parser recognized the header. */
  UNRECOGNIZED,

  /** The same file (by content hash) is already on this Thing. */
  DUPLICATE,
  PARSE_ERROR,
}
