package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.id.DataLogId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The last [capacity] parsed logs, in memory only (design §5.4): reopen in a session is instant. */
class DataLogCache(private val capacity: Int = 2) {
  private val mutex = Mutex()
  private val entries = LinkedHashMap<DataLogId, DataLogSeriesData>()

  suspend fun get(id: DataLogId): DataLogSeriesData? = mutex.withLock {
    entries.remove(id)
      ?.also { entries[id] = it }
  }

  suspend fun put(id: DataLogId, data: DataLogSeriesData) = mutex.withLock {
    entries.remove(id)
    entries[id] = data
    while (entries.size > capacity) entries.remove(entries.keys.first())
  }

  suspend fun remove(id: DataLogId) = mutex.withLock { entries.remove(id) }
}
