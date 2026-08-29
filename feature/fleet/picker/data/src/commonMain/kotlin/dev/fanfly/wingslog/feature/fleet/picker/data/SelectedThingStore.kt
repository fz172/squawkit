package dev.fanfly.wingslog.feature.fleet.picker.data

/**
 * Device-local memory of the thing the user last had selected in the fleet picker, so the app
 * reopens on the same thing after a restart. Deliberately not synced — it is a per-device UI
 * convenience, not account data.
 */
interface SelectedThingStore {
  /** The last-selected thing id, or null if none has been remembered on this device. */
  fun load(): String?

  /** Remember [thingId] as the current selection; null clears it. */
  fun save(thingId: String?)
}
