package dev.fanfly.wingslog.feature.fleet.picker.data

/**
 * Device-local memory of the aircraft the user last had selected in the fleet picker, so the app
 * reopens on the same aircraft after a restart. Deliberately not synced — it is a per-device UI
 * convenience, not account data.
 */
interface SelectedAircraftStore {
  /** The last-selected aircraft id, or null if none has been remembered on this device. */
  fun load(): String?

  /** Remember [aircraftId] as the current selection; null clears it. */
  fun save(aircraftId: String?)
}
