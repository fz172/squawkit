import FirebaseAuth
import FirebaseCore
import UserNotifications

/// Renders SquawkIt's N1 collaboration push while the app is backgrounded (design §7.6).
///
/// The server sends a data-only payload (mirrored by `PushPayload.swift`) plus a generic `alert`
/// so the OS invokes this extension at all (`apns.payload.aps["mutable-content"] = 1`); this class
/// rewrites that generic alert with the real localized title/body before the OS shows it.
///
/// Deliberately **pure Swift, no `ComposeApp.framework` linkage**. `ComposeApp.framework` is one
/// monolithic static Kotlin/Native build carrying Compose UI + Skia + the app's full DI graph
/// (`composeApp/build.gradle.kts`), and a notification service extension is capped by Apple at
/// roughly 24MB of runtime memory — linking it here would make that ceiling the central risk of this
/// feature. `PushPayload.swift` / `PushPayloadRendering.swift` / `NotificationTapEncoder.swift`
/// duplicate the equivalent commonMain Kotlin files field-for-field instead; keep them in sync by
/// hand if the server's wire format changes.
final class NotificationService: UNNotificationServiceExtension {

  private var contentHandler: ((UNNotificationContent) -> Void)?
  private var bestAttemptContent: UNMutableNotificationContent?

  override func didReceive(
    _ request: UNNotificationRequest,
    withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
  ) {
    self.contentHandler = contentHandler
    guard let bestAttemptContent = request.content.mutableCopy() as? UNMutableNotificationContent
    else {
      contentHandler(request.content)
      return
    }
    self.bestAttemptContent = bestAttemptContent

    guard let payload = PushPayload.parse(userInfo: request.content.userInfo) else {
      // Not a well-formed N1 message, or one from a newer server than this build understands.
      // Leave the generic alert fallback in place rather than posting a half-rendered tray entry.
      contentHandler(bestAttemptContent)
      return
    }

    if FirebaseApp.app() == nil {
      FirebaseApp.configure()
    }

    guard payload.isAddressedTo(signedInUid: Auth.auth().currentUser?.uid) else {
      // Addressed to another account that was previously signed in on this device (issue P4.13) —
      // its push_devices doc outlived an offline sign-out. Unlike Android, which can simply not post
      // the notification, iOS has already committed to showing *something* once `alert` is present:
      // the generic fallback stands as delivered rather than naming a tail number, actor, or squawk
      // title that belongs to someone else's account.
      contentHandler(bestAttemptContent)
      return
    }

    bestAttemptContent.title = payload.renderTitle()
    bestAttemptContent.body = payload.renderBody()
    var userInfo = bestAttemptContent.userInfo
    userInfo[NotificationTapEncoder.userInfoKey] = NotificationTapEncoder.encode(payload.tapTarget)
    bestAttemptContent.userInfo = userInfo

    contentHandler(bestAttemptContent)
  }

  override func serviceExtensionTimeWillExpire() {
    // Apple gives ~30s total; out of time means the generic alert fallback ships as delivered.
    if let contentHandler, let bestAttemptContent {
      contentHandler(bestAttemptContent)
    }
  }
}
