package dev.fanfly.wingslog.core.storage

/**
 * Narrow read-only view of the user's cloud-sync master toggle (persisted in the `sync_config`
 * table this module owns). Implemented by the sync feature's `SyncPreferences`; datamanagers
 * that need the signal inject this instead of depending on `feature:sync:data`, keeping the
 * sync engine the only module that knows sync internals.
 */
fun interface CloudSyncSetting {
  fun isCloudSyncEnabled(): Boolean
}
