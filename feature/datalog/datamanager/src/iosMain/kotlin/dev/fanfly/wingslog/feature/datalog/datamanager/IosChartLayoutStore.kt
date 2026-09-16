package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.DataLogId
import platform.Foundation.NSUserDefaults

/** Layout memory in [NSUserDefaults], one prefixed key per data log. */
class IosChartLayoutStore : ChartLayoutStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(id: DataLogId): String? =
    defaults.stringForKey(PREFIX + id.value)

  override fun save(id: DataLogId, encoded: String) {
    defaults.setObject(encoded, PREFIX + id.value)
  }

  override fun clear() {
    defaults.dictionaryRepresentation().keys
      .mapNotNull { it as? String }
      .filter { it.startsWith(PREFIX) }
      .forEach { defaults.removeObjectForKey(it) }
  }

  private companion object {
    const val PREFIX = "data_log_layout:"
  }
}
