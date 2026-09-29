import type { DocumentPage } from "../../src/ai/document/readDocument.js";
import type { SuggestionContext } from "../../src/ai/tasks/model.js";
import type { Draft, ValidatedDocument, ValidationInput } from "../../src/ai/tasks/validate/types.js";

/** A single-engine airplane with hours meters, one existing task, one log and a two-item pack. */
export function airplaneContext(overrides: Partial<SuggestionContext> = {}): SuggestionContext {
  return {
    templateId: "airplane",
    templateVersion: 13,
    specs: [
      { key: "make", label: "Make", value: "Sling" },
      { key: "model", label: "Model", value: "TSi" },
      { key: "tail_number", label: "Tail number", value: "N123AB" },
    ],
    components: [{ slotKey: "engine", make: "Rotax", model: "915 iS", spec: [] }],
    meters: [
      { key: "airframe_hours", unitLabel: "hrs", componentSlotKey: "", current: 412 },
      { key: "engine_hours", unitLabel: "hrs", componentSlotKey: "engine", current: 410 },
    ],
    existingTasks: [
      {
        id: "task-annual",
        title: "Annual inspection",
        componentSlotKey: "",
        rules: [{ kind: "time", every: 12, unit: "months" }],
        type: "routine",
        referenceNumber: "",
      },
    ],
    logs: [
      {
        id: "log-1",
        date: "2026-05-02",
        readings: [{ meterKey: "engine_hours", value: 380 }],
        title: "Oil change",
        workDescription: "Changed oil and filter",
        componentSlotKey: "engine",
      },
    ],
    logsTruncated: false,
    staticPack: [
      { title: "Oil change", description: "", componentSlotKey: "engine", rules: [] },
      { title: "ELT inspection", description: "", componentSlotKey: "", rules: [] },
    ],
    lexiconTaskNoun: "inspection",
    ...overrides,
  };
}

export function draft(overrides: Partial<Draft> = {}): Draft {
  return {
    title: "Replace spark plugs",
    rationale: "Rotax recommends it.",
    description: "",
    componentSlotKey: "engine",
    componentHint: "",
    rules: [],
    rawRules: [
      {
        kind: "meter",
        every: null,
        unit: null,
        meterKey: "engine_hours",
        interval: 200,
        months: null,
        dayOfMonth: null,
        description: null,
      },
    ],
    isOneTime: false,
    firstDue: null,
    rawFirstDue: [],
    type: "routine",
    referenceNumber: "",
    complianceAuthority: "",
    sourceKind: "document",
    citation: "Rotax 915 iS MM, rev 3",
    pageRef: "p. 5-12",
    sourcePages: [2],
    sourceDocument: "blob-mm",
    lastDone: null,
    lastDoneLogId: null,
    matchesExistingTaskId: "",
    intervalDifferenceNote: "",
    mergesStaticIndex: -1,
    preselect: false,
    confidence: "high",
    evidence: { documentIndex: 0, pages: [2], sourceFigures: [200] },
    ...overrides,
  };
}

export function page(n: number, text: string): DocumentPage {
  return { n, text, source: "text_layer" };
}

export function doc(overrides: Partial<ValidatedDocument> = {}): ValidatedDocument {
  return {
    blobId: "blob-mm",
    docType: "maintenance_manual",
    pages: [
      page(1, "BRP-Rotax Maintenance Manual Line 915 iS Edition 0 Rev 3"),
      page(2, "Scheduled maintenance. Replace spark plugs every 200 hours of operation."),
    ],
    matchesThing: true,
    ...overrides,
  };
}

export function input(overrides: Partial<ValidationInput> = {}): ValidationInput {
  return { context: airplaneContext(), documents: [doc()], today: "2026-09-29", ...overrides };
}
