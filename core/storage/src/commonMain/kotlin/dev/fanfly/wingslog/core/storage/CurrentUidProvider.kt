package dev.fanfly.wingslog.core.storage

/**
 * The signed-in account's uid, or null when signed out.
 *
 * `core:storage` deliberately knows nothing about Firebase, so authorship is supplied from outside —
 * the same shape as [CloudSyncSetting]. The store stamps it onto every local write as `writer_uid`,
 * which is what lets a shared log distinguish "this technician signed their own work" from "someone
 * else attributed the work to them" (design §7.5).
 */
fun interface CurrentUidProvider {
  fun currentUid(): String?
}
