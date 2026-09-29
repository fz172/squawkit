import type { MeterReadingValue, SuggestedRule } from "../model.js";
import type { FlatRule } from "../stageTypes.js";
import type { Draft, ValidationInput, Validator } from "./types.js";

/**
 * Stage 5 (design §6.7): deterministic rules, no model. Each is pure and runs in `VALIDATORS`
 * order; a later rule may rely on an earlier one (the meter rule needs typed rules).
 */

/** 1. Schema: typed rules of the kinds R22 allows, a title, and a slot the Thing fills. */
export const schemaRule: Validator = (drafts, { context }) => {
  const slots = new Set(context.components.map((c) => c.slotKey));
  return drafts.flatMap((d) => {
    const title = d.title.trim();
    if (!title) return [];
    const slot = slots.has(d.componentSlotKey) ? d.componentSlotKey : "";
    return [
      {
        ...d,
        title,
        componentSlotKey: slot,
        componentHint: slot ? d.componentHint : "",
        rules: d.rawRules.flatMap((r) => typeRule(r) ?? []),
      },
    ];
  });
};

/** 2. Meters (R23): only the Thing's meter keys; a task left with no rule goes on-condition. */
export const meterRule: Validator = (drafts, { context }) => {
  const meters = new Set(context.meters.map((m) => m.key));
  return drafts.map((d) => {
    const dropped = d.rules.filter((r) => r.kind === "meter" && !meters.has(r.meterKey));
    if (dropped.length === 0) return d;
    const kept = d.rules.filter((r) => !dropped.includes(r));
    if (kept.length > 0) return { ...d, rules: kept };
    const untracked = dropped
      .map((r) => (r.kind === "meter" ? `every ${r.interval} ${r.meterKey.replace(/_/g, " ")}` : ""))
      .join(" or ");
    return { ...d, rules: [{ kind: "on_condition", description: capitalize(untracked) }] };
  });
};

/**
 * 3. Regulatory typing (R18). Without documents everything is routine. With them, AD or SB typing
 * survives only when the cited document is that kind and prints the reference number.
 */
export const regulatoryRule: Validator = (drafts, { documents }) =>
  drafts.map((d) => {
    const routine = { ...d, type: "routine" as const, referenceNumber: "", complianceAuthority: "" };
    if (documents.length === 0) return routine;
    if (d.type === "routine") return d;
    const doc = d.evidence.documentIndex === null ? undefined : documents[d.evidence.documentIndex];
    const expected = d.type === "airworthiness_directive" ? "airworthiness_directive" : "service_bulletin";
    const holds =
      doc !== undefined &&
      doc.docType === expected &&
      d.referenceNumber.trim() !== "" &&
      containsVerbatim(doc.pages.map((p) => p.text).join("\n"), d.referenceNumber);
    return holds ? d : routine;
  });

/** 4. Citation (R18): a document suggestion whose cited pages do not state it is dropped. */
export const citationRule: Validator = (drafts, { documents }) =>
  drafts.filter((d) => {
    if (d.sourceKind !== "document") return true;
    const doc = d.evidence.documentIndex === null ? undefined : documents[d.evidence.documentIndex];
    if (!doc || d.evidence.pages.length === 0) return false;
    const text = doc.pages
      .filter((p) => d.evidence.pages.includes(p.n))
      .map((p) => p.text)
      .join("\n");
    return citationHolds(text, d.title, d.evidence.sourceFigures);
  });

/** 5. Dedup ids (R24, R25): only ids and indexes the context holds. */
export const dedupIdsRule: Validator = (drafts, { context }) => {
  const taskIds = new Set(context.existingTasks.map((t) => t.id));
  return drafts.map((d) => {
    const tracked = taskIds.has(d.matchesExistingTaskId);
    const inPack = d.mergesStaticIndex >= 0 && d.mergesStaticIndex < context.staticPack.length;
    return {
      ...d,
      matchesExistingTaskId: tracked ? d.matchesExistingTaskId : "",
      intervalDifferenceNote: tracked ? d.intervalDifferenceNote : "",
      mergesStaticIndex: inPack ? d.mergesStaticIndex : -1,
    };
  });
};

/** Last-done evidence (R29): a log the context holds; its date and reading come from that log. */
export const lastDoneRule: Validator = (drafts, { context }) =>
  drafts.map((d) => {
    const log = context.logs.find((l) => l.id === d.lastDoneLogId);
    if (!log) return { ...d, lastDone: null };
    const meterKeys = d.rules.flatMap((r) => (r.kind === "meter" ? [r.meterKey] : []));
    const reading = log.readings.find((r) => meterKeys.includes(r.meterKey)) ?? null;
    return { ...d, lastDone: { logId: log.id, date: log.date, reading } };
  });

/**
 * First due (R22): a one-time item's anchors made absolute from the Thing's current readings and
 * today, keeping the earliest date and the earliest reading. Recurring items carry none.
 */
export const firstDueRule: Validator = (drafts, { context, today }) =>
  drafts.map((d) => {
    if (!d.isOneTime) return { ...d, firstDue: null };
    let date: string | null = null;
    let meter: MeterReadingValue | null = null;
    for (const a of d.rawFirstDue) {
      if (!(a.value > 0)) continue;
      if (a.anchor === "time_from_now") {
        if (!a.unit) continue;
        const due = addToDate(today, Math.round(a.value), a.unit);
        if (date === null || due < date) date = due;
        continue;
      }
      const m = context.meters.find((x) => x.key === a.meterKey);
      if (!m) continue;
      const reading = a.anchor === "meter_reading" ? a.value : (m.current ?? 0) + a.value;
      if (meter === null || reading < meter.value) meter = { meterKey: m.key, value: reading };
    }
    return { ...d, firstDue: date || meter ? { date, meter } : null };
  });

/** 6. Pre-selection (R27). */
export const preselectRule: Validator = (drafts, { context, documents }) =>
  drafts.map((d) => {
    const doc = d.evidence.documentIndex === null ? undefined : documents[d.evidence.documentIndex];
    let preselect: boolean;
    if (d.matchesExistingTaskId) preselect = false;
    // A one-time item the Thing has already passed (a first service at 600 mi on a bike at
    // 1,200) is shown, since it may not have been done, but not ticked.
    else if (alreadyPassed(d, context)) preselect = false;
    else if (doc && !doc.matchesThing) preselect = false;
    else if (d.sourceKind === "document" || d.sourceKind === "logs") preselect = true;
    else preselect = context.templateId !== "airplane";
    return { ...d, preselect };
  });

/** 7. Confidence (R21a): weak items are dropped, never shown as weak. */
export const confidenceRule: Validator = (drafts) => drafts.filter((d) => d.confidence !== "low");

export const VALIDATORS: Validator[] = [
  schemaRule,
  meterRule,
  regulatoryRule,
  citationRule,
  dedupIdsRule,
  lastDoneRule,
  firstDueRule,
  preselectRule,
  confidenceRule,
];

export function validate(drafts: Draft[], input: ValidationInput): Draft[] {
  return VALIDATORS.reduce((acc, rule) => rule(acc, input), drafts);
}

/** Whitespace- and case-insensitive substring match, for reference numbers. */
export function containsVerbatim(text: string, needle: string): boolean {
  const squash = (s: string) => s.replace(/\s+/g, "").toLowerCase();
  const n = squash(needle);
  return n.length > 0 && squash(text).includes(n);
}

const STOPWORDS = new Set([
  "the", "and", "for", "with", "from", "into", "every", "each", "after", "before", "per", "all",
  "any", "its", "not", "are", "was", "has", "have", "this", "that", "your",
]);

/**
 * Whether page text states an item: half its title words appear, or one appears alongside one of
 * its interval figures. The eval scorer uses the same check for citation accuracy (§12.3).
 */
export function citationHolds(pageText: string, title: string, figures: number[]): boolean {
  const text = normalizeNumbers(pageText.toLowerCase());
  const tokens = [...new Set(title.toLowerCase().match(/[a-z][a-z-]{2,}/g) ?? [])].filter(
    (t) => !STOPWORDS.has(t),
  );
  const found = tokens.filter((t) => text.includes(t)).length;
  const share = tokens.length === 0 ? 0 : found / tokens.length;
  const figure = figures.some((f) => new RegExp(`(^|[^\\d.])${escape(formatFigure(f))}(?![\\d])`).test(text));
  if (tokens.length === 0) return figure;
  return share >= 0.5 || (figure && found > 0);
}

function typeRule(r: FlatRule): SuggestedRule | null {
  switch (r.kind) {
    case "time": {
      const every = Math.round(r.every ?? 0);
      return every > 0 && r.unit ? { kind: "time", every, unit: r.unit } : null;
    }
    case "meter": {
      const interval = r.interval ?? 0;
      return r.meterKey && interval > 0 ? { kind: "meter", meterKey: r.meterKey, interval } : null;
    }
    case "seasonal": {
      const months = [...new Set((r.months ?? []).filter((m) => Number.isInteger(m) && m >= 1 && m <= 12))];
      const day = r.dayOfMonth ?? 0;
      if (months.length === 0 || !Number.isInteger(day) || day < 0 || day > 31) return null;
      return { kind: "seasonal", months: months.sort((a, b) => a - b), dayOfMonth: day };
    }
    case "on_condition":
      return { kind: "on_condition", description: r.description ?? "" };
  }
}

function alreadyPassed(d: Draft, context: ValidationInput["context"]): boolean {
  const due = d.firstDue?.meter;
  if (!due) return false;
  const current = context.meters.find((m) => m.key === due.meterKey)?.current;
  return current !== null && current !== undefined && current > due.value;
}

/** `YYYY-MM-DD` plus a whole number of days, months or years; month ends clamp (Jan 31 + 1 month = Feb 28). */
function addToDate(iso: string, n: number, unit: "days" | "months" | "years"): string {
  const [y, m, d] = iso.split("-").map(Number);
  if (unit === "days") return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
  const months = unit === "years" ? n * 12 : n;
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

/** "10,000" and "10 000" read as 10000. */
function normalizeNumbers(text: string): string {
  return text.replace(/(\d)[,  ](?=\d{3}(\D|$))/g, "$1");
}

function formatFigure(f: number): string {
  return Number.isInteger(f) ? String(f) : String(Number(f.toFixed(2)));
}

function escape(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

