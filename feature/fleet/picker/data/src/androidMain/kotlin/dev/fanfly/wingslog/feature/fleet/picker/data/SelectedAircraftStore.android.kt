package dev.fanfly.wingslog.feature.fleet.picker.data

import android.content.Context

/** Device-local selected-aircraft memory backed by [android.content.SharedPreferences]. */
class AndroidSelectedAircraftStore(context: Context) : SelectedAircraftStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): String? = prefs.getString(KEY, null)

  override fun save(aircraftId: String?) {
    prefs.edit()
      .apply { if (aircraftId == null) remove(KEY) else putString(KEY, aircraftId) }
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "fleet_picker_prefs"
    const val KEY = "selected_aircraft_id"
  }
}
