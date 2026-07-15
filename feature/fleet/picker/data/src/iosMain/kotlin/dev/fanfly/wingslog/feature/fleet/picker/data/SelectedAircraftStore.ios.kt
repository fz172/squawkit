package dev.fanfly.wingslog.feature.fleet.picker.data

import platform.Foundation.NSUserDefaults

/** Device-local selected-aircraft memory backed by [NSUserDefaults]. */
class IosSelectedAircraftStore : SelectedAircraftStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(): String? = defaults.stringForKey(KEY)

  override fun save(aircraftId: String?) {
    if (aircraftId == null) defaults.removeObjectForKey(KEY)
    else defaults.setObject(aircraftId, KEY)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
