package dev.fanfly.wingslog.feature.fleet.picker.data

import android.content.Context
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.ThingId

/** Device-local selected-thing memory backed by [android.content.SharedPreferences]. */
class AndroidSelectedThingStore(context: Context) : SelectedThingStore {
  private val prefs =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun load(): ThingId? = prefs.getString(KEY, null)?.let(::ThingId)

  override fun save(thingId: ThingId?) {
    prefs.edit()
      .apply { if (thingId == null) remove(KEY) else putString(KEY, thingId.value) }
      .apply()
  }

  private companion object {
    const val PREFS_NAME = "fleet_picker_prefs"
    const val KEY = "selected_aircraft_id"
  }
}
