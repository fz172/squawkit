import Foundation

/// A template's words, as the notification text needs them — the iOS extension's copy of the
/// lexicons the app renders a push in (#1426).
///
/// The push names its Thing's template (`templateId`); the extension has no template registry, so it
/// carries this table instead. **Do not edit it by hand:** `IosPushMirrorTest.kt`
/// (feature/notifications/viewing) builds every line from the app's own lexicons and prints the
/// whole table when it disagrees, so a changed word or a new template is a paste from that failure.
struct PushNouns {
  let thing: String
  let thingTitle: String
  let squawk: String
  let squawksTitle: String
  let task: String
  let tasks: String
  let tasksTitle: String
  let log: String
  let logsTitle: String
  let dataLog: String
  let dataLogsTitle: String

  static let byTemplate: [String: PushNouns] = [
    "airplane": PushNouns(thing: "aircraft", thingTitle: "Aircraft", squawk: "squawk", squawksTitle: "Squawks", task: "maintenance task", tasks: "maintenance tasks", tasksTitle: "Maintenance Tasks", log: "work log", logsTitle: "Work Logs", dataLog: "data log", dataLogsTitle: "Data Logs Archive"),
    "automotive": PushNouns(thing: "vehicle", thingTitle: "Vehicle", squawk: "issue", squawksTitle: "Issues", task: "service", tasks: "services", tasksTitle: "Services", log: "service record", logsTitle: "Service Records", dataLog: "data log", dataLogsTitle: "Data Logs"),
    "bike": PushNouns(thing: "bike", thingTitle: "Bike", squawk: "issue", squawksTitle: "Issues", task: "service", tasks: "services", tasksTitle: "Services", log: "service record", logsTitle: "Service Records", dataLog: "data log", dataLogsTitle: "Data Logs"),
    "boat": PushNouns(thing: "boat", thingTitle: "Boat", squawk: "issue", squawksTitle: "Issues", task: "service", tasks: "services", tasksTitle: "Services", log: "service record", logsTitle: "Service Records", dataLog: "data log", dataLogsTitle: "Data Logs"),
    "custom": PushNouns(thing: "thing", thingTitle: "Thing", squawk: "issue", squawksTitle: "Issues", task: "task", tasks: "tasks", tasksTitle: "Tasks", log: "log", logsTitle: "Logs", dataLog: "data log", dataLogsTitle: "Data Logs"),
    "home": PushNouns(thing: "home", thingTitle: "Home", squawk: "attention item", squawksTitle: "Attention Items", task: "maintenance task", tasks: "maintenance tasks", tasksTitle: "Maintenance Tasks", log: "work log", logsTitle: "Work Logs", dataLog: "data log", dataLogsTitle: "Data Logs"),
  ]

  /// A template this build does not know — one added by a newer release. Mirrors the app's
  /// `GenericLexicon`, which `TemplateRegistry.lexiconFor` falls back to for the same case.
  static let generic = PushNouns(thing: "thing", thingTitle: "Thing", squawk: "issue", squawksTitle: "Issues", task: "task", tasks: "tasks", tasksTitle: "Tasks", log: "log", logsTitle: "Logs", dataLog: "data log", dataLogsTitle: "Data Logs")

  /// The words for [templateId]. Absent means a server older than #1426, from before any push said;
  /// every push then read as an aeroplane, so that stays the answer rather than a change of words.
  static func forTemplate(_ templateId: String?) -> PushNouns {
    guard let templateId else { return byTemplate["airplane"]! }
    return byTemplate[templateId] ?? generic
  }
}
