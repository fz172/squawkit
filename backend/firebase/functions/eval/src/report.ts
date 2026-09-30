import type { CaseScore } from "./score.js";

/** PRD §9.4's numbers for document cases. */
export const BAR = { recall: 0.9, intervalAccuracy: 0.95, citationAccuracy: 0.95, p90LatencyMs: 180_000 };

/** Not a PRD gate: a soft target, since every document suggestion is pre-selected (R27). */
export const PRECISION_TARGET = 0.7;

export type RunSummary = {
  gates: {
    unsupportedRegulatory: number;
    unverbatimReferences: number;
    uncitedDocumentItems: number;
    foreignMeterKeys: number;
    invalidOutputRuns: number;
    forbidden: number;
    passed: boolean;
  };
  document: {
    runs: number;
    recall: number | null;
    intervalAccuracy: number | null;
    citationAccuracy: number | null;
    precision: number | null;
    duplicates: number;
    /** Over runs with three or more documents, the PRD's timing case. */
    p90LatencyMs: number | null;
    meetsBar: boolean;
  };
  runs: number;
  failedRuns: number;
  meanCostMicros: number;
  meanCostPerDocumentMicros: number | null;
  warmP90LatencyMs: number | null;
};

export function summarize(scores: CaseScore[], warmScores: CaseScore[], documentCounts: Map<string, number>): RunSummary {
  const sum = (f: (s: CaseScore) => number) => scores.reduce((acc, s) => acc + f(s), 0);
  const gates = {
    unsupportedRegulatory: sum((s) => s.gates.unsupportedRegulatory.length),
    unverbatimReferences: sum((s) => s.gates.unverbatimReferences.length),
    uncitedDocumentItems: sum((s) => s.gates.uncitedDocumentItems.length),
    foreignMeterKeys: sum((s) => s.gates.foreignMeterKeys.length),
    invalidOutputRuns: scores.filter((s) => !s.gates.validOutput).length,
    forbidden: sum((s) => s.forbidden.length),
  };

  const doc = scores.filter((s) => s.kind === "document");
  const matches = doc.flatMap((s) => s.matches);
  const cited = matches.filter((m) => m.citationOk !== null);
  const expected = doc.reduce((acc, s) => acc + s.expected, 0);
  const document = {
    runs: doc.length,
    recall: ratio(doc.reduce((acc, s) => acc + s.matched, 0), expected),
    intervalAccuracy: ratio(matches.filter((m) => m.intervalOk).length, matches.length),
    citationAccuracy: ratio(cited.filter((m) => m.citationOk).length, cited.length),
    precision: ratio(
      doc.reduce((acc, s) => acc + (s.precision === null ? 0 : s.documentMatched), 0),
      doc.reduce((acc, s) => acc + (s.precision === null ? 0 : s.documentSuggestions), 0),
    ),
    duplicates: doc.reduce((acc, s) => acc + s.duplicates.length, 0),
    p90LatencyMs: p90(doc.filter((s) => (documentCounts.get(s.caseId) ?? 0) >= 3).map((s) => s.latencyMs)),
  };

  const documents = scores.reduce((acc, s) => acc + (documentCounts.get(s.caseId) ?? 0), 0);
  return {
    gates: { ...gates, passed: Object.values(gates).every((n) => n === 0) },
    document: {
      ...document,
      meetsBar:
        document.runs > 0 &&
        atLeast(document.recall, BAR.recall) &&
        atLeast(document.intervalAccuracy, BAR.intervalAccuracy) &&
        atLeast(document.citationAccuracy, BAR.citationAccuracy) &&
        (document.p90LatencyMs === null || document.p90LatencyMs < BAR.p90LatencyMs),
    },
    runs: scores.length,
    failedRuns: scores.filter((s) => s.status === "failed").length,
    meanCostMicros: scores.length === 0 ? 0 : sum((s) => s.costMicros) / scores.length,
    meanCostPerDocumentMicros: documents === 0 ? null : sum((s) => s.costMicros) / documents,
    warmP90LatencyMs: p90(warmScores.map((s) => s.latencyMs)),
  };
}

export type ReportConfig = Record<string, string | number | boolean>;

export function renderReport(config: ReportConfig, summary: RunSummary, scores: CaseScore[]): string {
  const lines: string[] = [];
  lines.push(`# Task suggestion eval`, "");
  lines.push(Object.entries(config).map(([k, v]) => `\`${k}=${v}\``).join(" · "), "");

  const g = summary.gates;
  lines.push(`## Hard gates: ${g.passed ? "PASS" : "FAIL"}`, "");
  lines.push("| Gate | Violations |", "|---|---|");
  lines.push(`| AD/SB typed without that document | ${g.unsupportedRegulatory} |`);
  lines.push(`| Reference number not verbatim in the document | ${g.unverbatimReferences} |`);
  lines.push(`| Document suggestion its cited page does not state | ${g.uncitedDocumentItems} |`);
  lines.push(`| Meter key outside the template | ${g.foreignMeterKeys} |`);
  lines.push(`| Runs without valid output after one retry | ${g.invalidOutputRuns} |`);
  lines.push(`| Suggested a must-not-appear task | ${g.forbidden} |`, "");

  const d = summary.document;
  lines.push(`## Document cases: ${d.meetsBar ? "meets the bar" : "below the bar"} (${d.runs} runs)`, "");
  lines.push("| Measure | Result | Bar |", "|---|---|---|");
  lines.push(`| Recall | ${pct(d.recall)} | ≥ ${pct(BAR.recall)} |`);
  lines.push(`| Interval accuracy | ${pct(d.intervalAccuracy)} | ≥ ${pct(BAR.intervalAccuracy)} |`);
  lines.push(`| Citation accuracy | ${pct(d.citationAccuracy)} | ≥ ${pct(BAR.citationAccuracy)} |`);
  lines.push(`| p90 latency, 3+ documents | ${secs(d.p90LatencyMs)} | < ${secs(BAR.p90LatencyMs)} |`);
  lines.push(`| Precision (soft target, not a gate) | ${pct(d.precision)} | ≥ ${pct(PRECISION_TARGET)} |`, "");
  lines.push(`${d.duplicates} document suggestions duplicate a task another suggestion matched.`, "");

  lines.push(`## Cost and speed`, "");
  lines.push(`- ${summary.runs} runs, ${summary.failedRuns} failed`);
  lines.push(`- Mean cost per run: ${dollars(summary.meanCostMicros)}`);
  lines.push(`- Mean cost per document: ${summary.meanCostPerDocumentMicros === null ? "—" : dollars(summary.meanCostPerDocumentMicros)}`);
  lines.push(`- p90 latency on a warm cache: ${secs(summary.warmP90LatencyMs)} (R19: < 5 s)`, "");

  lines.push(`## Cases`, "");
  lines.push(
    "| Case | Status | Suggestions | Recall | Intervals | Citations | Precision | Duplicates | Not in key | Cost | Time | Reviewed |",
    "|---|---|---|---|---|---|---|---|---|---|---|---|",
  );
  for (const s of scores) {
    const status = s.status === "failed" ? `failed: ${s.errorCode}` : s.status;
    lines.push(
      `| ${s.caseId} | ${status} | ${s.suggestions} ${sources(s)} | ${pct(s.recall)} | ${pct(s.intervalAccuracy)} | ${pct(s.citationAccuracy)} | ${pct(s.precision)} | ${s.duplicates.length} | ${s.invented.length} | ${dollars(s.costMicros)} | ${secs(s.latencyMs)} | ${s.reviewed ? "yes" : "no"} |`,
    );
  }
  lines.push("");

  for (const s of scores) {
    const notes = [
      ...(s.statusOk === false ? [`ended ${s.status}, expected otherwise`] : []),
      ...s.missed.map((t) => `missed: ${t}`),
      ...s.matches.filter((m) => !m.intervalOk).map((m) => `interval differs: ${m.expected} ↔ ${m.suggestion}`),
      ...s.matches.filter((m) => m.citationOk === false).map((m) => `wrong page: ${m.suggestion}`),
      ...s.duplicates.map((t) => `duplicate: ${t}`),
      ...s.invented.map((t) => `not in the answer key: ${t}`),
      ...s.forbidden.map((t) => `must not appear: ${t}`),
      ...Object.entries(s.gates).flatMap(([gate, v]) => (Array.isArray(v) ? v.map((x) => `${gate}: ${x}`) : [])),
    ];
    if (notes.length === 0) continue;
    lines.push(`### ${s.caseId}`, "", ...notes.map((n) => `- ${n}`), "");
  }
  return lines.join("\n");
}

function sources(s: CaseScore): string {
  const parts = Object.entries(s.bySource).map(([k, n]) => `${k.replace(/_/g, " ")} ${n}`);
  return parts.length ? `(${parts.join(", ")})` : "";
}

function ratio(n: number, d: number): number | null {
  return d === 0 ? null : n / d;
}

function p90(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.ceil(sorted.length * 0.9) - 1];
}

function atLeast(value: number | null, bar: number): boolean {
  return value !== null && value >= bar;
}

function pct(v: number | null): string {
  return v === null ? "—" : `${Math.round(v * 100)}%`;
}

function secs(ms: number | null): string {
  return ms === null ? "—" : `${(ms / 1000).toFixed(1)} s`;
}

function dollars(micros: number): string {
  return `$${(micros / 1_000_000).toFixed(3)}`;
}
