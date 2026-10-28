package dev.fanfly.wingslog.feature.fleet.picker.data

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.ThingId
import kotlinx.browser.localStorage

/** Device-local selected-thing memory backed by the browser's `localStorage`. */
class JsSelectedThingStore : SelectedThingStore {
  override fun load(): ThingId? = localStorage.getItem(KEY)?.let(::ThingId)

  override fun save(thingId: ThingId?) {
    if (thingId == null) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, thingId.value)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
