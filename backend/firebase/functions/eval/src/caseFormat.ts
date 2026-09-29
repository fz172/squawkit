import { readFileSync, readdirSync, existsSync } from "node:fs";
import path from "node:path";

import type { ComplianceKind, SourceDocumentRef, SuggestedRule, SuggestTasksRequest } from "../../src/ai/tasks/model.js";

/**
 * An eval case (design §12.2): `cases/<id>/case.json` is the request, `expected.json` the
 * hand-written answer. Documents are not in the repo; they live in `docs/<sha256>.<ext>`,
 * fetched by fetch.sh or registered by add-doc.sh.
 */
export type EvalCase = {
  id: string;
  description: string;
  /** Document cases are held to PRD §9.4's numbers; no-document cases are judged by hand. */
  kind: "document" | "no_document";
  request: SuggestTasksRequest;
};

export type ExpectedTask = {
  /** Matched against suggestion titles, loosely: any alias will do. */
  titleAliases: string[];
  /** The rules as the Thing should carry them; empty for on-condition items. */
  rules: SuggestedRule[];
  /**
   * Other rule sets the manual itself allows, when its interval depends on a condition the Thing
   * does not record (a certified oil, severe service). Matching any counts as a correct interval.
   */
  alternativeRules?: SuggestedRule[][];
  /**
   * For a one-time item, the reading it first falls due at, counted from new. Checked with the
   * interval (within 2%). A date is not, since it depends on the day of the run.
   */
  firstDueMeter?: { meterKey: string; value: number };
  /**
   * Where the item is stated: a document's `blobId` and its PDF page numbers, or a regulation the
   * interval comes from instead of the documents ("14 CFR § 91.413"). A task several sources
   * state lists each; citing any document one counts. Regulation citations are for the reader.
   */
  citations?: ExpectedCitation[];
  type?: ComplianceKind;
  /** Acceptable if suggested, not counted against recall if missing. */
  optional?: boolean;
  /** A hard failure if suggested, e.g. an AD the run was never given. */
  mustNotAppear?: boolean;
};

export type ExpectedCitation = { document: string; pages: number[] } | { regulation: string };

export type Expected = {
  /** False until someone who knows the schedule has checked the list. */
  reviewed: boolean;
  /** When set, the run must end this way: a Thing with only a name should come back empty. */
  expectedStatus?: "succeeded" | "empty";
  tasks: ExpectedTask[];
};

export type LoadedCase = { evalCase: EvalCase; expected: Expected };

export function loadCases(casesDir: string, only: string[] | "all"): LoadedCase[] {
  const ids = readdirSync(casesDir, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name)
    .sort();
  const wanted = only === "all" ? ids : only;
  const missing = wanted.filter((id) => !ids.includes(id));
  if (missing.length > 0) throw new Error(`Unknown case: ${missing.join(", ")}`);
  return wanted.map((id) => {
    const evalCase = readJson<EvalCase>(path.join(casesDir, id, "case.json"));
    if (evalCase.id !== id) throw new Error(`${id}/case.json says it is ${evalCase.id}`);
    const expectedPath = path.join(casesDir, id, "expected.json");
    const expected = existsSync(expectedPath)
      ? readJson<Expected>(expectedPath)
      : { reviewed: false, tasks: [] };
    const problems = checkExpected(evalCase, expected);
    if (problems.length > 0) throw new Error(`${id}/expected.json:\n  ${problems.join("\n  ")}`);
    return { evalCase, expected };
  });
}

/** Mistakes that would silently score as misses: unknown documents, meters and rule shapes. */
export function checkExpected(evalCase: EvalCase, expected: Expected): string[] {
  const documents = new Set(evalCase.request.documents.map((d) => d.blobId));
  const meters = new Set(evalCase.request.context.meters.map((m) => m.key));
  return expected.tasks.flatMap((task, i) => {
    const name = task.titleAliases?.[0] ?? `task ${i}`;
    const problems: string[] = [];
    if (!task.titleAliases?.length) problems.push(`${name}: no titleAliases`);
    for (const rules of [task.rules, ...(task.alternativeRules ?? [])]) {
      for (const r of rules ?? []) {
        const bad =
          (r.kind === "meter" && (!meters.has(r.meterKey) || !(r.interval > 0))) ||
          (r.kind === "time" && (!(r.every > 0) || !["days", "months", "years"].includes(r.unit))) ||
          (r.kind === "seasonal" && !r.months?.every((m) => m >= 1 && m <= 12)) ||
          !["meter", "time", "seasonal", "on_condition"].includes(r.kind);
        if (bad) problems.push(`${name}: bad rule ${JSON.stringify(r)}`);
      }
    }
    if (task.firstDueMeter && (!meters.has(task.firstDueMeter.meterKey) || !(task.firstDueMeter.value > 0))) {
      problems.push(`${name}: bad firstDueMeter ${JSON.stringify(task.firstDueMeter)}`);
    }
    for (const c of task.citations ?? []) {
      if ("regulation" in c) {
        if (!c.regulation?.trim()) problems.push(`${name}: empty regulation citation`);
        continue;
      }
      if (!documents.has(c.document)) problems.push(`${name}: no document ${c.document} in the case`);
      if (!c.pages?.length || !c.pages.every((n) => Number.isInteger(n) && n >= 1)) {
        problems.push(`${name}: bad pages ${JSON.stringify(c.pages)}`);
      }
    }
    return problems;
  });
}

const EXTENSIONS: Record<string, string> = {
  "application/pdf": "pdf",
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

export function documentPath(docsDir: string, ref: SourceDocumentRef): string {
  const ext = EXTENSIONS[ref.mimeType];
  if (!ext) throw new Error(`No extension for ${ref.mimeType}`);
  return path.join(docsDir, `${ref.sha256}.${ext}`);
}

function readJson<T>(file: string): T {
  return JSON.parse(readFileSync(file, "utf8")) as T;
}
