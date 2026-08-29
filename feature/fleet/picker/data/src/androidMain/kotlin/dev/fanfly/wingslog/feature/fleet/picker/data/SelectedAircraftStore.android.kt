package dev.fanfly.wingslog.feature.fleet.picker.data

import android.content.Context

/** Device-local selected-thing memory backed by [android.content.SharedPreferences]. */
class AndroidSelectedThingStore(context: Context) : SelectedThingStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): String? = prefs.getString(KEY, null)

  override fun save(thingId: String?) {
    prefs.edit()
      .apply { if (thingId == null) remove(KEY) else putString(KEY, thingId) }
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "fleet_picker_prefs"
    const val KEY = "selected_aircraft_id"
  }
}
