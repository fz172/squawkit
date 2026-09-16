package dev.fanfly.wingslog.feature.datalog.datamanager

import android.content.Context
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.DataLogId

/** Layout memory in its own [android.content.SharedPreferences] file, one entry per data log. */
class AndroidChartLayoutStore(context: Context) : ChartLayoutStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(id: DataLogId): String? = prefs.getString(id.value, null)

  override fun save(id: DataLogId, encoded: String) {
    prefs.edit()
      .putString(id.value, encoded)
      .apply()
  }

  override fun clear() {
    prefs.edit()
      .clear()
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "data_log_layouts"
  }
}
