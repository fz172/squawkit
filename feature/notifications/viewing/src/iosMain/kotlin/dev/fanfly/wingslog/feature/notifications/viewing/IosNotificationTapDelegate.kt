package dev.fanfly.wingslog.feature.notifications.viewing

import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * `userInfo` key carrying the encoded [NotificationTapTarget][dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget].
 * Written by [IosLocalNotifier], read back by [IosNotificationTapDelegate] — the iOS equivalent of
 * the intent data Android's tap `PendingIntent` carries.
 */
internal const val TAP_URI_USER_INFO_KEY = "wingslog_tap_uri"

/**
 * Receives taps on delivered notifications and hands the target to [NotificationTapRouter], the same
 * place Android's `MainActivity.handleDeepLink` puts it (design §5.3).
 *
 * Written in Kotlin/Native rather than Swift, unlike the Google/Apple sign-in and ads bridges: those
 * exist because Kotlin/Native cannot link those SDKs at all, whereas `UserNotifications` is a system
 * framework with interop already — [IosLocalNotifier] posts through it directly. Keeping the tap
 * here puts the encode and the decode in one module, as on Android.
 *
 * Install via `MainEntry.registerNotificationTapHandler()` from
 * `application(_:didFinishLaunchingWithOptions:)`. It must be assigned before the app finishes
 * launching or iOS drops a tap that cold-started the process — the exact case that matters most,
 * since that is what a pilot tapping a notification from the lock screen does.
 */
class IosNotificationTapDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

  /**
   * A tap (or an action / dismissal, which carry their own identifiers we do not register — anything
   * without our key is simply ignored). [NotificationTapRouter] retains the target until the shell
   * composes, so a cold start is no different from a warm one.
   */
  override fun userNotificationCenter(
    center: UNUserNotificationCenter,
    didReceiveNotificationResponse: UNNotificationResponse,
    withCompletionHandler: () -> Unit,
  ) {
    val uri = didReceiveNotificationResponse.notification.request.content
      .userInfo[TAP_URI_USER_INFO_KEY] as? String
    if (uri != null) NotificationTapRouter.deliver(uri)
    withCompletionHandler()
  }

  /**
   * Show the banner even while the app is foregrounded. iOS suppresses foreground notifications
   * unless a delegate says otherwise; Android and web both show them, and an urgency alert that
   * silently does not appear because the pilot happened to have the app open is the one case where
   * suppression is most wrong — they are looking at the aircraft the alert is about.
   */
  override fun userNotificationCenter(
    center: UNUserNotificationCenter,
    willPresentNotification: UNNotification,
    withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
  ) {
    withCompletionHandler(
      UNNotificationPresentationOptionBanner or
        UNNotificationPresentationOptionList or
        UNNotificationPresentationOptionSound,
    )
  }
}
