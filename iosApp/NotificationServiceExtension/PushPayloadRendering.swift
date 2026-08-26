import Foundation

/// Mirrors `PushPayloadRendering.kt` — same `titleKey`/`bodyKey` contract and the same argument
/// order per key, with the English string bodies inlined from
/// `feature/notifications/sharedassets/src/commonMain/composeResources/values/strings.xml` (the only
/// locale that exists today). Keep both files in sync by hand if either changes — see
/// `NotificationService.swift`'s doc comment for why this isn't a shared Kotlin call instead.
extension PushPayload {

  func renderTitle() -> String {
    switch titleKey {
    case "notification_n1_title":
      // "%1$s · %2$s" — tail number, then the TITLE-CASE section label.
      return "\(tailNumber) · \(sectionTitle())"
    case "notification_n1_title_high_volume":
      return "\(tailNumber) · A lot of activity"
    // The escalation titles carry no dynamic content: the tail number lives in their bodies.
    case "notification_title_grounded":
      return "⚠ AOG"
    case "notification_title_priority_raised":
      return "Priority raised"
    case "notification_n1_title_squawk_created":
      return "New squawk"
    default:
      return ""
    }
  }

  func renderBody() -> String {
    switch bodyKey {
    case "notification_n1_body_single":
      // "%1$s made a change to %2$s" — actor, then the LOWER-CASE section label.
      return "\(actor()) made a change to \(sectionLower())"
    case "notification_n1_body_plural":
      // "%1$s made %2$d changes to %3$s" — the count sits between the two, not after them.
      return "\(actor()) made \(changeCount) changes to \(sectionLower())"
    case "notification_n1_body_high_volume":
      return "Too many changes to list. Open the aircraft to catch up."
    // The escalation bodies lead with the tail number: tail, actor, then the squawk's own title on
    // its own line.
    case "notification_n1_body_squawk_created":
      return "\(tailNumber): \(actor()) created a new squawk issue\n\n\(recordTitle)"
    case "notification_n1_body_squawk_raised":
      return "\(tailNumber): \(actor()) raised the priority of 1 squawk issue\n\n\(recordTitle)"
    default:
      return ""
    }
  }

  /// The server sends an empty name when the share roster read came back empty — a revoked or
  /// unsynced member.
  private func actor() -> String {
    actorName.isEmpty ? "A collaborator" : actorName
  }

  /// Resolved from `recordType` rather than sent — the server has no way to produce localized
  /// section labels (§7.6). An unknown type falls back to the aircraft labels.
  private func sectionTitle() -> String {
    switch recordType {
    case "squawk": return "Squawks"
    case "task": return "Tasks"
    case "log": return "Logbook"
    default: return "Aircraft"
    }
  }

  private func sectionLower() -> String {
    switch recordType {
    case "squawk": return "squawks"
    case "task": return "tasks"
    case "log": return "logbook entries"
    default: return "the aircraft"
    }
  }
}
