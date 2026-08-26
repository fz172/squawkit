package dev.fanfly.wingslog.feature.notifications.model

/**
 * Who this device is signed in as, for the one question push delivery has to ask before it renders
 * anything: is this message addressed to the account currently using this device (issue P4.13)?
 *
 * The same seam shape, and for the same reason, as [PushTokenSink]. The check runs where the push
 * arrives — `viewing`, the platform SDK's callback — while the answer lives in `core:auth`, which
 * `viewing` does not depend on and should not start depending on for one nullable string. A `fun
 * interface` in `:model` closes it without either side learning about the other.
 *
 * Synchronous on purpose: the FCM callback is already on a background thread with a bounded budget,
 * and "who is signed in" is a field read on a live auth object, not a network call.
 */
fun interface SignedInUid {
  /** The signed-in account, or `null` when nobody is signed in on this device. */
  fun current(): String?
}
