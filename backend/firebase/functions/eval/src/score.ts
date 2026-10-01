import type { DocumentPage } from "../../src/ai/document/readDocument.js";
import type { AiErrorCode } from "../../src/ai/errors.js";
import type { PipelineCallRecord, PipelineOutcome } from "../../src/ai/tasks/pipeline.js";
import type { SuggestedRule, TaskSourceKind, TaskSuggestion } from "../../src/ai/tasks/model.js";
import { citationHolds, containsVerbatim } from "../../src/ai/tasks/validate/validators.js";
import type { EvalCase, Expected, ExpectedTask } from "./caseFormat.js";

/** One run of one case, scored (design §12.3). */
export type CaseScore = {
  caseId: string;
  kind: EvalCase["kind"];
  reviewed: boolean;
  status: "succeeded" | "empty" | "failed";
  errorCode: AiErrorCode | null;
  /** Null when the case does not say how it should end. */
  statusOk: boolean | null;
  suggestions: number;
  bySource: Partial<Record<TaskSourceKind, number>>;
  /** Null when the case has no required expected tasks. */
  recall: number | null;
  intervalAccuracy: number | null;
  citationAccuracy: number | null;
  expected: number;
  matched: number;
  matches: Array<{ expected: string; suggestion: string; intervalOk: boolean; citationOk: boolean | null }>;
  missed: string[];
  /**
   * Of the document-sourced suggestions, the share matching an expected task (required or
   * optional), each task counted once. Null without an answer key or document suggestions.
   */
  precision: number | null;
  documentSuggestions: number;
  documentMatched: number;
  /** Document suggestions for a task another suggestion already matched. */
  duplicates: string[];
  /** Document-sourced suggestions matching no expected task. */
  invented: string[];
  /** Suggestions matching a `mustNotAppear` task. */
  forbidden: string[];
  gates: HardGates;
  costMicros: number;
  latencyMs: number;
  retries: number;
  cacheHits: number;
};

/** PRD §9.4's hard gates, checked here independently of the validators. Each lists offenders. */
export type HardGates = {
  unsupportedRegulatory: string[];
  unverbatimReferences: string[];
  uncitedDocumentItems: string[];
  foreignMeterKeys: string[];
  validOutput: boolean;
};

export type ScoreInput = {
  evalCase: EvalCase;
  expected: Expected;
  outcome: PipelineOutcome | { status: "failed"; errorCode: AiErrorCode | null };
  pagesByBlob: Map<string, DocumentPage[]>;
  calls: PipelineCallRecord[];
  latencyMs: number;
};

/** A title word overlap this high, alias to title, counts as the same task. */
const TITLE_MATCH = 0.6;

export function scoreCase(input: ScoreInput): CaseScore {
  const { evalCase, expected, outcome } = input;
  const suggestions = outcome.status === "failed" ? [] : outcome.result.suggestions;
  const documents = outcome.status === "failed" ? [] : outcome.result.documents;
  const meterKeys = new Set(evalCase.request.context.meters.map((m) => m.key));
  const pagesText = (blobId: string, pages?: number[]) =>
    (input.pagesByBlob.get(blobId) ?? [])
      .filter((p) => pages === undefined || pages.includes(p.n))
      .map((p) => p.text)
      .join("\n");

  const pairs = assign(expected.tasks, suggestions);
  const required = expected.tasks.filter((t) => !t.optional && !t.mustNotAppear);
  const matchedRequired = pairs.filter((p) => !p.task.optional && !p.task.mustNotAppear);

  const matches = pairs
    .filter((p) => !p.task.mustNotAppear)
    .map((p) => ({
      expected: p.task.titleAliases[0],
      suggestion: p.suggestion.title,
      intervalOk: intervalMatches(p.task, p.suggestion),
      citationOk:
        p.suggestion.sourceKind !== "document"
          ? null
          : documentCitations(p.task).length > 0
            ? documentCitations(p.task).some(
                (c) =>
                  c.document === p.suggestion.sourceDocument &&
                  p.suggestion.sourcePages.some((n) => c.pages.includes(n)),
              )
            : citedPageStates(p.suggestion, pagesText),
    }));
  const cited = matches.filter((m) => m.citationOk !== null);
  const matchedIds = new Set(pairs.map((p) => p.suggestion.suggestionId));
  const listed = expected.tasks.filter((t) => !t.mustNotAppear);
  const documentSuggestions = suggestions.filter((s) => s.sourceKind === "document");
  const unmatched = documentSuggestions.filter((s) => !matchedIds.has(s.suggestionId));
  const isDuplicate = (s: TaskSuggestion) =>
    listed.some((t) => t.titleAliases.some((a) => titleSimilarity(a, s.title) >= TITLE_MATCH));
  const matchedDocument = pairs.filter((p) => !p.task.mustNotAppear && p.suggestion.sourceKind === "document");

  const bySource: CaseScore["bySource"] = {};
  for (const s of suggestions) bySource[s.sourceKind] = (bySource[s.sourceKind] ?? 0) + 1;

  return {
    caseId: evalCase.id,
    kind: evalCase.kind,
    reviewed: expected.reviewed,
    status: outcome.status,
    errorCode: outcome.status === "failed" ? outcome.errorCode : null,
    statusOk: expected.expectedStatus ? outcome.status === expected.expectedStatus : null,
    suggestions: suggestions.length,
    bySource,
    recall: required.length === 0 ? null : matchedRequired.length / required.length,
    intervalAccuracy: ratio(matches.filter((m) => m.intervalOk).length, matches.length),
    citationAccuracy: ratio(cited.filter((m) => m.citationOk).length, cited.length),
    expected: required.length,
    matched: matchedRequired.length,
    matches,
    missed: required.filter((t) => !pairs.some((p) => p.task === t)).map((t) => t.titleAliases[0]),
    precision: listed.length === 0 ? null : ratio(matchedDocument.length, documentSuggestions.length),
    documentSuggestions: documentSuggestions.length,
    documentMatched: matchedDocument.length,
    duplicates: unmatched.filter(isDuplicate).map((s) => s.title),
    invented: unmatched.filter((s) => !isDuplicate(s)).map((s) => s.title),
    forbidden: pairs.filter((p) => p.task.mustNotAppear).map((p) => p.suggestion.title),
    gates: {
      unsupportedRegulatory: suggestions
        .filter((s) => s.type !== "routine")
        .filter((s) => documents.find((d) => d.blobId === s.sourceDocument)?.docType !== s.type)
        .map((s) => s.title),
      unverbatimReferences: suggestions
        .filter((s) => s.referenceNumber && !containsVerbatim(pagesText(s.sourceDocument), s.referenceNumber))
        .map((s) => `${s.title} (${s.referenceNumber})`),
      uncitedDocumentItems: suggestions
        .filter((s) => s.sourceKind === "document" && !citedPageStates(s, pagesText))
        .map((s) => s.title),
      foreignMeterKeys: suggestions.flatMap((s) =>
        s.rules.flatMap((r) => (r.kind === "meter" && !meterKeys.has(r.meterKey) ? [`${s.title}: ${r.meterKey}`] : [])),
      ),
      validOutput: !(outcome.status === "failed" && outcome.errorCode === "invalid_output"),
    },
    costMicros: input.calls.reduce((sum, c) => sum + c.usage.costMicros, 0),
    latencyMs: input.latencyMs,
    retries: input.calls.filter((c) => c.attempts > 1).length,
    cacheHits: input.calls.filter((c) => c.cacheHit).length,
  };
}

/** Whether a document suggestion's cited pages state it: the §6.7 rule 4 check. */
function citedPageStates(s: TaskSuggestion, pagesText: (blobId: string, pages?: number[]) => string): boolean {
  if (s.sourcePages.length === 0) return false;
  const figures = [
    ...s.rules.flatMap((r) => (r.kind === "meter" ? [r.interval] : r.kind === "time" ? [r.every] : [])),
    ...(s.firstDue?.meter ? [s.firstDue.meter.value] : []),
  ];
  return citationHolds(pagesText(s.sourceDocument, s.sourcePages), s.title, figures);
}

/**
 * One-to-one assignment, best pairs first: title similarity decides whether a pair can match at
 * all, and a matching interval breaks ties between candidate titles.
 */
function assign(tasks: ExpectedTask[], suggestions: TaskSuggestion[]) {
  const candidates = tasks.flatMap((task) =>
    suggestions.flatMap((suggestion) => {
      const title = Math.max(...task.titleAliases.map((a) => titleSimilarity(a, suggestion.title)));
      if (title < TITLE_MATCH) return [];
      return [{ task, suggestion, score: title + (intervalMatches(task, suggestion) ? 1 : 0) }];
    }),
  );
  candidates.sort((a, b) => b.score - a.score);
  const usedTasks = new Set<ExpectedTask>();
  const usedSuggestions = new Set<TaskSuggestion>();
  const pairs: Array<{ task: ExpectedTask; suggestion: TaskSuggestion }> = [];
  for (const c of candidates) {
    if (usedTasks.has(c.task) || usedSuggestions.has(c.suggestion)) continue;
    usedTasks.add(c.task);
    usedSuggestions.add(c.suggestion);
    pairs.push({ task: c.task, suggestion: c.suggestion });
  }
  return pairs;
}

/** 1 when one title contains the other, else the share of the alias's words found in the title. */
export function titleSimilarity(alias: string, title: string): number {
  const a = normalizeTitle(alias);
  const t = normalizeTitle(title);
  if (!a || !t) return 0;
  if (t.includes(a) || a.includes(t)) return 1;
  const words = a.split(" ");
  const titleWords = new Set(t.split(" "));
  return words.filter((w) => titleWords.has(w)).length / words.length;
}

function documentCitations(task: ExpectedTask) {
  return (task.citations ?? []).flatMap((c) => ("document" in c ? [c] : []));
}

function intervalMatches(task: ExpectedTask, s: TaskSuggestion): boolean {
  const rules = [task.rules, ...(task.alternativeRules ?? [])].some((r) => rulesMatch(r, s.rules));
  if (!task.firstDueMeter) return rules;
  const due = s.firstDue?.meter;
  const want = task.firstDueMeter;
  return rules && !!due && due.meterKey === want.meterKey && Math.abs(due.value - want.value) <= want.value * 0.02;
}

/**
 * Same rules, ignoring order and on-condition entries (no rules and "on condition" are equal).
 * Months and years compare as months; meter intervals within 2%.
 */
export function rulesMatch(expectedRules: SuggestedRule[], actualRules: SuggestedRule[]): boolean {
  const expected = expectedRules.filter((r) => r.kind !== "on_condition");
  const actual = actualRules.filter((r) => r.kind !== "on_condition");
  if (expected.length !== actual.length) return false;
  const remaining = [...actual];
  for (const e of expected) {
    const i = remaining.findIndex((a) => ruleEquals(e, a));
    if (i < 0) return false;
    remaining.splice(i, 1);
  }
  return true;
}

function ruleEquals(a: SuggestedRule, b: SuggestedRule): boolean {
  if (a.kind === "time" && b.kind === "time") return timeKey(a) === timeKey(b);
  if (a.kind === "meter" && b.kind === "meter") {
    return a.meterKey === b.meterKey && Math.abs(a.interval - b.interval) <= a.interval * 0.02;
  }
  if (a.kind === "seasonal" && b.kind === "seasonal") return a.months.join(",") === b.months.join(",");
  return a.kind === "on_condition" && b.kind === "on_condition";
}

function timeKey(r: Extract<SuggestedRule, { kind: "time" }>): string {
  if (r.unit === "days") return `${r.every}d`;
  return `${r.unit === "years" ? r.every * 12 : r.every}m`;
}

/** Lowercase words, with "hr"/"hrs"/"h" read as "hour" and plurals as singular. */
function normalizeTitle(s: string): string {
  return s
    .toLowerCase()
    .replace(/(\d)([a-z])/g, "$1 $2")
    .replace(/[^a-z0-9]+/g, " ")
    .trim()
    .split(" ")
    .map((w) => (/^(h|hr|hrs|hours)$/.test(w) ? "hour" : w.length > 3 && /[^s]s$/.test(w) ? w.slice(0, -1) : w))
    .join(" ");
}

function ratio(n: number, d: number): number | null {
  return d === 0 ? null : n / d;
}
