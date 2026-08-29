package dev.fanfly.wingslog.feature.fleet.picker.data

import kotlinx.browser.localStorage

/** Device-local selected-thing memory backed by the browser's `localStorage`. */
class JsSelectedThingStore : SelectedThingStore {
  override fun load(): String? = localStorage.getItem(KEY)

  override fun save(thingId: String?) {
    if (thingId == null) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, thingId)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
