package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.id.DataLogId

/**
 * The last layout used on each data log, on this device only (PRD R31, design §5.5): one encoded
 * line per log through the platform's key-value store, never synced, wiped with the local data.
 */
interface ChartLayoutStore {
  fun load(id: DataLogId): String?
  fun save(id: DataLogId, encoded: String)
  fun clear()
}
