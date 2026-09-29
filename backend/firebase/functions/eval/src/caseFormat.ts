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
  /** PDF page numbers that state the item, for citation accuracy. */
  pageRefs?: number[];
  type?: ComplianceKind;
  /** Acceptable if suggested, not counted against recall if missing. */
  optional?: boolean;
  /** A hard failure if suggested, e.g. an AD the run was never given. */
  mustNotAppear?: boolean;
};

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
    return { evalCase, expected };
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
