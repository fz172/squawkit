import type { ComplianceKind, DocType } from "./model.js";

/**
 * What each model stage returns, exactly as its schema (schemas.ts) describes it. Nullable fields
 * are null rather than absent, because portable schemas require every property.
 */

export type IntervalUnit =
  | "hours"
  | "days"
  | "weeks"
  | "months"
  | "years"
  | "miles"
  | "kilometers"
  | "cycles"
  | "landings"
  | "starts"
  | "other";

/** An interval as the source states it, before any conversion to the Thing's meters. */
export type SourceInterval = { value: number; unit: IntervalUnit };

export type Confidence = "high" | "medium" | "low";

/** A schedule item found in one document. Thing-independent, so it is cacheable (§6.2). */
export type ExtractedItem = {
  title: string;
  description: string;
  /** For an inspection event, its checklist lines (PRD decision 3). */
  checklist: string[];
  intervals: SourceInterval[];
  isOneTime: boolean;
  /** The pipeline's page numbers (the `=== page N ===` markers), 1-based. */
  pages: number[];
  /** The page reference as printed, e.g. "5-12", when there is one. */
  printedPageRef: string | null;
  /** The part it applies to, in the document's words: "engine", "propeller", "airframe". */
  componentHint: string | null;
  type: ComplianceKind;
  referenceNumber: string | null;
  complianceAuthority: string | null;
};

export type DocumentIdentity = {
  manufacturer: string;
  models: string[];
  title: string;
  revision: string | null;
  docType: DocType;
  referenceNumber: string | null;
};

export type ExtractOutput = { document: DocumentIdentity; items: ExtractedItem[] };

export type RecalledItem = {
  title: string;
  description: string;
  checklist: string[];
  intervals: SourceInterval[];
  isOneTime: boolean;
  componentHint: string | null;
  sourceKind: "manufacturer_schedule" | "common_practice";
  /** The publication a manufacturer-schedule item is attributed to. */
  publication: string | null;
};

export type RecallOutput = { identityConfidence: Confidence; items: RecalledItem[] };

export type LocateOutput = { pages: number[] };

/** A rule as the tailor writes it, flat so the schema stays portable. validate/rules.ts types it. */
export type FlatRule = {
  kind: "time" | "meter" | "seasonal" | "on_condition";
  every: number | null;
  unit: "days" | "months" | "years" | null;
  meterKey: string | null;
  interval: number | null;
  months: number[] | null;
  dayOfMonth: number | null;
  description: string | null;
};

/**
 * Where a one-time item falls due, as the source anchors it. The validator turns each into an
 * absolute reading or date from the context, so the model never does the arithmetic.
 * - `meter_reading`: at this reading ("first service at 600 mi" is odometer 600).
 * - `meter_from_now`: this far past the current reading ("within 25 hours").
 * - `time_from_now`: this long from today ("within 3 months").
 */
export type FlatFirstDue = {
  anchor: "meter_reading" | "meter_from_now" | "time_from_now";
  meterKey: string | null;
  value: number;
  unit: "days" | "months" | "years" | null;
};

export type TailoredSuggestion = {
  /** The candidates this merges: `d<doc>.<item>` for document items, `r<item>` for recalled. */
  candidateIds: string[];
  title: string;
  rationale: string;
  description: string;
  componentSlotKey: string | null;
  componentHint: string | null;
  rules: FlatRule[];
  isOneTime: boolean;
  /** For a one-time item: where it first falls due. Empty otherwise. */
  firstDue: FlatFirstDue[];
  lastDoneLogId: string | null;
  matchesExistingTaskId: string | null;
  intervalDifferenceNote: string | null;
  mergesStaticIndex: number | null;
  confidence: Confidence;
};

export type TailorOutput = {
  suggestions: TailoredSuggestion[];
  documents: Array<{ index: number; matchesThing: boolean }>;
  /**
   * Starter-pack (curated) items that do not fit this thing, with why: an engine oil change on a
   * battery-electric car (tasks-5).
   */
  notApplicable: Array<{ index: number; reason: string }>;
};
