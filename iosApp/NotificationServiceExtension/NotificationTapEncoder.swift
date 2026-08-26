import Foundation

/// Mirrors `NotificationTapRouter.encode` (`NotificationTapRouter.kt:59-65`) — same
/// `wingslog://notification-tap/…` format — and writes under the same `userInfo` key
/// `IosLocalNotifier` uses (`TAP_URI_USER_INFO_KEY`, `IosNotificationTapDelegate.kt:18`), so a tap on
/// a notification this extension rendered routes through the same `IosNotificationTapDelegate`
/// running in the main app process (design §5.3).
enum NotificationTapEncoder {
  static let userInfoKey = "wingslog_tap_uri"

  static func encode(_ target: NotificationTapTarget) -> String {
    switch target {
    case .squawk(let aircraftId, let squawkId):
      return "wingslog://notification-tap/squawk/\(aircraftId)/\(squawkId)"
    case .task(let aircraftId, let taskId):
      return "wingslog://notification-tap/task/\(aircraftId)/\(taskId)"
    case .log(let aircraftId, let logId):
      return "wingslog://notification-tap/log/\(aircraftId)/\(logId)"
    case .aircraft(let aircraftId, let tab):
      let base = "wingslog://notification-tap/aircraft/\(aircraftId)"
      return tab.map { "\(base)?tab=\($0)" } ?? base
    }
  }
}
