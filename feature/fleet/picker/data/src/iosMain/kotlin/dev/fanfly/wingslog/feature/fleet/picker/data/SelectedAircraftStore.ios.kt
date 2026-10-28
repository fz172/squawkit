package dev.fanfly.wingslog.feature.fleet.picker.data

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.ThingId
import platform.Foundation.NSUserDefaults

/** Device-local selected-thing memory backed by [NSUserDefaults]. */
class IosSelectedThingStore : SelectedThingStore {
  private val defaults = NSUserDefaults.standardUserDefaults

  override fun load(): ThingId? = defaults.stringForKey(KEY)?.let(::ThingId)

  override fun save(thingId: ThingId?) {
    if (thingId == null) defaults.removeObjectForKey(KEY)
    else defaults.setObject(thingId.value, KEY)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
