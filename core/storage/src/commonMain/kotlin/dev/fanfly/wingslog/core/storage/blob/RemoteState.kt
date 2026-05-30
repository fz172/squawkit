package dev.fanfly.wingslog.core.storage.blob

import app.cash.sqldelight.ColumnAdapter

/**
 * The four states a blob can be in relative to Firebase Storage. See docs/storage/storage_r2_design.md §5.
 *
 * Transitions:
 * - `LOCAL_ONLY → UPLOADING → SYNCED` — the upload happy path.
 * - `UPLOADING → LOCAL_ONLY` — transient failure; retried with backoff.
 * - `REMOTE_ONLY → SYNCED` — fresh device sees a proto referencing an attachment, lazy-downloads
 *   on first open.
 *
 * The on-disk column is `TEXT` (the [wireName]); a SQLDelight [ColumnAdapter] handles the mapping.
 */
sealed interface RemoteState {
  /** Stable wire name persisted in `blob_object.remote_state`. Never change once shipped. */
  val wireName: String

  /** Local file present, no upload yet (or upload failed transient). */
  data object LocalOnly : RemoteState {
    override val wireName = "LOCAL_ONLY"
  }

  /** Upload in flight. */
  data object Uploading : RemoteState {
    override val wireName = "UPLOADING"
  }

  /** Local file present and remote copy exists in Firebase Storage. */
  data object Synced : RemoteState {
    override val wireName = "SYNCED"
  }

  /**
   * Remote copy exists but no local file yet — placeholder created from a pulled proto. First
   * open downloads the bytes and transitions to [Synced].
   */
  data object RemoteOnly : RemoteState {
    override val wireName = "REMOTE_ONLY"
  }

  companion object {
    val ALL: List<RemoteState> = listOf(LocalOnly, Uploading, Synced, RemoteOnly)

    private val byWire: Map<String, RemoteState> = ALL.associateBy { it.wireName }

    fun fromWire(wire: String): RemoteState =
      byWire[wire] ?: error("Unknown remote_state '$wire' — register it in RemoteState")
  }
}

/** SQLDelight `ColumnAdapter` that maps the `TEXT` `remote_state` column to [RemoteState]. */
val remoteStateAdapter: ColumnAdapter<RemoteState, String> =
  object : ColumnAdapter<RemoteState, String> {
    override fun decode(databaseValue: String): RemoteState = RemoteState.fromWire(databaseValue)
    override fun encode(value: RemoteState): String = value.wireName
  }
