import Foundation

/// Mirrors `PushPayloadRendering.kt` — same `titleKey`/`bodyKey` contract and the same argument
/// order per key, with the English string bodies inlined from
/// `feature/notifications/sharedassets/src/commonMain/composeResources/values/strings.xml` (the only
/// locale that exists today). Keep both files in sync by hand if either changes — see
/// `NotificationService.swift`'s doc comment for why this isn't a shared Kotlin call instead.
///
/// **The extension has no Thing lexicon.** The push does not say which template the Thing uses, so
/// every noun below is the airplane template's — what the Kotlin renderer produces for an aeroplane.
extension PushPayload {

  func renderTitle() -> String {
    switch titleKey {
    case "notification_n1_title":
      // "%1$s · %2$s" — tail number, then the TITLE-CASE section label.
      return "\(tailNumber) · \(sectionTitle())"
    // The escalation title carries no dynamic content: the tail number lives in the body.
    case "notification_title_priority_raised":
      return "Priority raised"
    case "notification_n1_title_squawk_created":
      return "New squawk"
    // A suggestion run ended (task population PRD R20).
    case "notification_suggestions_title":
      return "\(tailNumber) · Suggestions"
    default:
      return ""
    }
  }

  func renderBody() -> String {
    switch bodyKey {
    case "notification_suggestions_body_ready":
      return "Your suggested maintenance tasks are ready to review"
    case "notification_suggestions_body_empty":
      return "Not enough to go on. Add details for better suggestions."
    case "notification_suggestions_body_failed":
      return "Suggestions didn’t finish. Try again from the task list."
    // "%1$s: %2$s VERB a %3$s\n\n%4$s" — tail, actor, the record's own noun, then its own title on
    // its own line.
    case "notification_n1_body_record_created":
      return "\(tailNumber): \(actor()) created a new \(noun())\n\n\(recordTitle)"
    case "notification_n1_body_record_updated":
      return "\(tailNumber): \(actor()) updated a \(noun())\n\n\(recordTitle)"
    case "notification_n1_body_record_deleted":
      return "\(tailNumber): \(actor()) deleted a \(noun())\n\n\(recordTitle)"
    // The Thing has no per-record title to name, so its body is two segments, not four.
    case "notification_n1_body_thing_updated":
      return "\(tailNumber): \(actor()) updated the aircraft"
    // The escalation bodies lead with the tail number: tail, actor, then the squawk's own title on
    // its own line.
    case "notification_n1_body_squawk_created":
      return "\(tailNumber): \(actor()) created a new squawk\n\n\(recordTitle)"
    case "notification_n1_body_squawk_raised":
      return "\(tailNumber): \(actor()) raised the priority of 1 squawk\n\n\(recordTitle)"
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
  /// section labels (§7.6). An unknown type falls back to the Thing's label.
  private func sectionTitle() -> String {
    switch recordType {
    case "squawk": return "Squawks"
    case "task": return "Maintenance Tasks"
    case "log": return "Work Logs"
    case "data_log": return "Data Logs Archive"
    default: return "Aircraft"
    }
  }

  /// The record's own singular noun for the body — "a squawk", "a maintenance task", "a work log".
  private func noun() -> String {
    switch recordType {
    case "squawk": return "squawk"
    case "task": return "maintenance task"
    case "data_log": return "data log"
    default: return "work log"
    }
  }
}
