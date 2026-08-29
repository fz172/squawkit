package dev.fanfly.wingslog.feature.fleet.picker.data

import platform.Foundation.NSUserDefaults

/** Device-local selected-thing memory backed by [NSUserDefaults]. */
class IosSelectedThingStore : SelectedThingStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(): String? = defaults.stringForKey(KEY)

  override fun save(thingId: String?) {
    if (thingId == null) defaults.removeObjectForKey(KEY)
    else defaults.setObject(thingId, KEY)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
