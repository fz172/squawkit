package dev.fanfly.wingslog.feature.sync.data

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.serialization.Serializable

/**
 * On-the-wire representation of one synced entity revision.
 *
 * The payload is base64 — Firestore would also accept a native blob, but base64 is the only form
 * that round-trips cleanly through GitLive's commonMain serialization on both Android and iOS
 * (their per-platform extensions diverge for byte arrays). 33% size overhead is irrelevant for
 * proto blobs at the scales we ship.
 *
 * `lastUpdateTimestamp` is typed [BaseTimestamp] so writes can pass [Timestamp.ServerTimestamp] (the
 * sentinel that asks Firestore to stamp the doc) and reads see a real [Timestamp].
 */
@Serializable
internal data class SyncDocWire(
  val payload: String,
  val deleted: Boolean,
  val schema: String,
  val lastUpdateTimestamp: BaseTimestamp,
)

internal fun BaseTimestamp.epochMsOrNull(): Long? = (this as? Timestamp)?.toMilliseconds()?.toLong()
