package dev.fanfly.wingslog.feature.notifications.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the user is looking at right now, as the [NotificationTapTarget]s a push could name. A push
 * whose tap would land on one of them is not shown: it would only announce what is already on
 * screen. The suggestions screen registers here so its own run's "ready" push stays quiet while it
 * is open (task population PRD R20).
 *
 * A screen registers only while resumed, so an app in the background with that screen open still
 * gets the push. A list, not a set: the same screen can be on the back stack twice, and leaving one
 * copy must not unregister the other. Thread-safe, since the Android push service asks from FCM's
 * background thread.
 */
object OnScreenTapTargets {

  private val shown = MutableStateFlow<List<NotificationTapTarget>>(emptyList())

  /** Marks [target] as on screen until the returned function is called. */
  fun show(target: NotificationTapTarget): () -> Unit {
    shown.update { it + target }
    return { shown.update { it - target } }
  }

  fun isOnScreen(target: NotificationTapTarget): Boolean = target in shown.value
}
