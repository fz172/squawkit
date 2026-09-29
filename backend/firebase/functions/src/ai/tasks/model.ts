/**
 * The pipeline's request and result (design §4.2), as plain types. The wire protos arrive in T05
 * and map onto these; the eval harness writes them as JSON.
 */

export type ComplianceKind = "routine" | "service_bulletin" | "airworthiness_directive";

export type TaskSourceKind = "document" | "manufacturer_schedule" | "common_practice" | "logs";

export type SuggestedRule =
  | { kind: "time"; every: number; unit: "days" | "months" | "years" }
  | { kind: "meter"; meterKey: string; interval: number }
  | { kind: "seasonal"; months: number[]; dayOfMonth: number }
  | { kind: "on_condition"; description: string };

export type SpecValue = { key: string; label: string; value: string };

/** A filled component slot. No serial, by construction (R12). */
export type ComponentSummary = { slotKey: string; make: string; model: string; spec: SpecValue[] };

export type MeterSummary = {
  key: string;
  unitLabel: string;
  componentSlotKey: string;
  /** The current reading, or null when nothing has been logged. */
  current: number | null;
};

export type ExistingTask = {
  id: string;
  title: string;
  componentSlotKey: string;
  rules: SuggestedRule[];
  type: ComplianceKind;
  referenceNumber: string;
};

export type MeterReadingValue = { meterKey: string; value: number };

/** A log entry with no technician, cost, attachment or comment field (R12). */
export type LogSummary = {
  id: string;
  /** ISO date, `YYYY-MM-DD`. */
  date: string;
  readings: MeterReadingValue[];
  title: string;
  workDescription: string;
  componentSlotKey: string;
};

export type StaticPackItem = {
  title: string;
  description: string;
  componentSlotKey: string;
  rules: SuggestedRule[];
};

export type SuggestionContext = {
  templateId: string;
  templateVersion: number;
  specs: SpecValue[];
  components: ComponentSummary[];
  meters: MeterSummary[];
  existingTasks: ExistingTask[];
  logs: LogSummary[];
  logsTruncated: boolean;
  staticPack: StaticPackItem[];
  lexiconTaskNoun: string;
};

export type SourceDocumentRef = {
  blobId: string;
  name: string;
  mimeType: string;
  sha256: string;
  sizeBytes: number;
};

export type SuggestTasksRequest = {
  thingId: string;
  context: SuggestionContext;
  documents: SourceDocumentRef[];
};

export type DocType =
  | "maintenance_manual"
  | "owners_manual"
  | "service_bulletin"
  | "service_instruction"
  | "airworthiness_directive"
  | "appliance_manual"
  | "other";

export type IdentifiedDocument = {
  blobId: string;
  name: string;
  manufacturer: string;
  title: string;
  revision: string;
  docType: DocType;
  /** False when the document looks like it is for a different Thing (R8a). */
  matchesThing: boolean;
};

export type LastDoneEvidence = { logId: string; date: string; reading: MeterReadingValue | null };

export type TaskSuggestion = {
  suggestionId: string;
  title: string;
  rationale: string;
  description: string;
  /** Empty files the task at Thing level. */
  componentSlotKey: string;
  /** "Engine #2", in words, when the Thing has several of the slot (design §7.4). */
  componentHint: string;
  rules: SuggestedRule[];
  isOneTime: boolean;
  type: ComplianceKind;
  referenceNumber: string;
  complianceAuthority: string;
  sourceKind: TaskSourceKind;
  citation: string;
  pageRef: string;
  /** The document's blob id, for a document source. */
  sourceDocument: string;
  lastDone: LastDoneEvidence | null;
  /** An existing task this duplicates: shown as Already tracked (R24). */
  matchesExistingTaskId: string;
  intervalDifferenceNote: string;
  /** Index into `context.staticPack` this replaces, or -1 (R25). */
  mergesStaticIndex: number;
  preselect: boolean;
};

export type SuggestTasksResult = {
  suggestions: TaskSuggestion[];
  documents: IdentifiedDocument[];
  generationVersion: string;
};
