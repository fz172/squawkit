package dev.fanfly.wingslog.feature.notifications.viewing

import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives N1 collaboration push (design §5.5, §7.6) and renders it here on the device.
 *
 * The server sends **data-only** messages precisely so this runs: a notification-type message is
 * drawn by the OS itself when the app is backgrounded, which would bypass the per-channel routing
 * [AndroidLocalNotifier] sets up and the tap router that makes a tap land on the right record.
 *
 * Two jobs, and the split is the whole reason `:viewing` can host this at all (§3):
 * - **Render.** Turning a data map into a [dev.fanfly.wingslog.feature.notifications.model.PendingNotification]
 *   is display work, and this module already owns display.
 * - **Forward the token.** `onNewToken` fires here, but the registrar that writes it lives in
 *   `:datamanager`, which `:viewing` may not depend on. [PushTokenSink] in `:model` closes that —
 *   this class never learns what a token is *for*.
 */
class WingsLogFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {

  private val notifier: LocalNotifier by inject()

  /**
   * `injectOrNull` is not a thing, and a hard `inject()` is right here: this service only exists on
   * Android, and Android always binds a registrar (`platformPushTokenModule.android`). If that ever
   * stops being true, failing loudly beats silently dropping every token.
   */
  private val tokenSink: PushTokenSink by inject()

  /**
   * Posted under the payload's own `notificationId`, which is what makes §7.3's tray replacement
   * happen: the second push of a burst reuses the id and *replaces* the first entry rather than
   * stacking beside it.
   *
   * `runBlocking` rather than a launched coroutine, and deliberately: FCM calls this on a
   * background thread and allows roughly 20 seconds, while a coroutine launched into a scope this
   * service owns could outlive the service and be cancelled mid-post. The work is a resource lookup
   * and a `NotificationManager.notify` — short enough that blocking the thread FCM gave us is the
   * simpler correct answer.
   */
  override fun onMessageReceived(message: RemoteMessage) {
    val parsed = PushPayload.parse(message.data)
    if (parsed == null) {
      // Not an N1 message, or one from a newer server than this build understands. Dropping it is
      // better than posting a tray entry with no id, which could never be replaced or cancelled.
      log.d { "Ignoring a push with no usable N1 payload (keys=${message.data.keys})" }
      return
    }
    runBlocking {
      runCatching { notifier.post(parsed.toPendingNotification()) }
        .onFailure { log.w(it) { "Could not post an N1 push (id=${parsed.notificationId})" } }
    }
  }

  /**
   * Fires on first registration and on every rotation. Forwarded straight through — the registrar
   * decides whether there is an account to attach it to, and re-writes the doc idempotently.
   */
  override fun onNewToken(token: String) {
    runBlocking {
      runCatching { tokenSink.onTokenRefreshed(token) }
        .onFailure { log.w(it) { "Could not forward a refreshed push token" } }
    }
  }

  private companion object {
    val log = Logger.withTag("WingsLogFirebaseMessagingService")
  }
}
