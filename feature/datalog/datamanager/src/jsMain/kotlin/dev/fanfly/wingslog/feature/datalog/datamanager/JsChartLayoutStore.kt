package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.DataLogId
import kotlinx.browser.localStorage

/** Layout memory in the browser's `localStorage`, one prefixed key per data log. */
class JsChartLayoutStore : ChartLayoutStore {
  override fun load(id: DataLogId): String? = localStorage.getItem(PREFIX + id.value)

  override fun save(id: DataLogId, encoded: String) {
    localStorage.setItem(PREFIX + id.value, encoded)
  }

  override fun clear() {
    val keys = (0 until localStorage.length).mapNotNull { localStorage.key(it) }.filter { it.startsWith(PREFIX) }
    keys.forEach { localStorage.removeItem(it) }
  }

  private companion object {
    const val PREFIX = "data_log_layout:"
  }
}
