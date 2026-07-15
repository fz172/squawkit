package dev.fanfly.wingslog.feature.fleet.picker.data

import kotlinx.browser.localStorage

/** Device-local selected-aircraft memory backed by the browser's `localStorage`. */
class JsSelectedAircraftStore : SelectedAircraftStore {
  override fun load(): String? = localStorage.getItem(KEY)

  override fun save(aircraftId: String?) {
    if (aircraftId == null) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, aircraftId)
  }

  private companion object {
    const val KEY = "selected_aircraft_id"
  }
}
