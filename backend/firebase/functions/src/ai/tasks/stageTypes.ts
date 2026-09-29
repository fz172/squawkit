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
  lastDoneLogId: string | null;
  matchesExistingTaskId: string | null;
  intervalDifferenceNote: string | null;
  mergesStaticIndex: number | null;
  confidence: Confidence;
};

export type TailorOutput = {
  suggestions: TailoredSuggestion[];
  documents: Array<{ index: number; matchesThing: boolean }>;
};
