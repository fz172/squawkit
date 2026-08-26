import Foundation

/// Mirrors `NotificationTapTarget.kt` (commonMain), field-for-field.
enum NotificationTapTarget {
  case aircraft(aircraftId: String, tab: String?)
  case squawk(aircraftId: String, squawkId: String)
  case task(aircraftId: String, taskId: String)
  case log(aircraftId: String, logId: String)
}

/// A decoded N1 push payload — the Swift-side mirror of
/// `feature/notifications/viewing/src/commonMain/.../PushPayload.kt` (design §7.6).
///
/// Duplicated here on purpose rather than linking the Kotlin module — see
/// `NotificationService.swift`'s doc comment for why. Keep this in sync by hand if the server's wire
/// format changes; `PushPayloadParsingTest.kt` names every case this file also needs to handle.
struct PushPayload {
  let notificationId: String
  let highPriority: Bool
  let titleKey: String
  let bodyKey: String
  let tailNumber: String
  let actorName: String
  let recordType: String
  let changeCount: Int
  let recordTitle: String
  let tapTarget: NotificationTapTarget
  let recipientUid: String?

  /// Same three-way logic as `PushPayload.kt:60-64`: no `recipientUid` (a server older than P4.13)
  /// means render; nobody signed in on this device means drop; addressed to a different account
  /// means drop — the fix for issue P4.13 (a stale `push_devices` doc surviving an offline sign-out).
  func isAddressedTo(signedInUid: String?) -> Bool {
    guard let recipientUid else { return true }
    guard let signedInUid else { return false }
    return recipientUid == signedInUid
  }

  /// Returns `nil` for anything that isn't a well-formed N1 message, same contract as
  /// `PushPayload.kt:73-94` — an unrecognised push is dropped rather than shown half-rendered.
  static func parse(userInfo: [AnyHashable: Any]) -> PushPayload? {
    guard let notificationId = userInfo["notificationId"] as? String, !notificationId.isEmpty else {
      return nil
    }
    guard let tapTarget = parseTapTarget(userInfo["tapTarget"] as? String) else { return nil }
    // Blank is treated as absent, same as PushPayload.kt:92 — an empty string addresses nobody.
    let recipientUidRaw = userInfo["recipientUid"] as? String
    let recipientUid = (recipientUidRaw?.isEmpty ?? true) ? nil : recipientUidRaw
    return PushPayload(
      notificationId: notificationId,
      // FCM data values arrive as strings, never a real boolean — "true"/"false", not JSON.
      highPriority: (userInfo["highPriority"] as? String) == "true",
      titleKey: (userInfo["titleKey"] as? String) ?? "",
      bodyKey: (userInfo["bodyKey"] as? String) ?? "",
      tailNumber: (userInfo["tailNumber"] as? String) ?? "",
      actorName: (userInfo["actorName"] as? String) ?? "",
      recordType: (userInfo["recordType"] as? String) ?? "",
      changeCount: Int((userInfo["changeCount"] as? String) ?? "") ?? 1,
      recordTitle: (userInfo["recordTitle"] as? String) ?? "",
      tapTarget: tapTarget,
      recipientUid: recipientUid
    )
  }

  /// Colon-delimited (`squawk:{aircraftId}:{squawkId}`, `aircraft:{aircraftId}:{tab}`), mirroring
  /// `PushPayload.kt:103-121`. A record variant with no id falls back to the aircraft, since a tap
  /// with nothing to scroll to still lands the pilot on the right aircraft.
  private static func parseTapTarget(_ raw: String?) -> NotificationTapTarget? {
    guard let raw else { return nil }
    let parts = raw.components(separatedBy: ":")
    guard parts.count >= 2, !parts[1].isEmpty else { return nil }
    let aircraftId = parts[1]
    let recordId = (parts.count > 2 && !parts[2].isEmpty) ? parts[2] : nil
    switch parts[0] {
    case "aircraft":
      return .aircraft(aircraftId: aircraftId, tab: recordId)
    case "squawk":
      return recordId.map { NotificationTapTarget.squawk(aircraftId: aircraftId, squawkId: $0) }
        ?? .aircraft(aircraftId: aircraftId, tab: "squawks")
    case "task":
      return recordId.map { NotificationTapTarget.task(aircraftId: aircraftId, taskId: $0) }
        ?? .aircraft(aircraftId: aircraftId, tab: "tasks")
    case "log":
      return recordId.map { NotificationTapTarget.log(aircraftId: aircraftId, logId: $0) }
        ?? .aircraft(aircraftId: aircraftId, tab: "logs")
    default:
      return nil
    }
  }
}
