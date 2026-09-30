import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import path from "node:path";

import type { PipelineOutcome } from "../../src/ai/tasks/pipeline.js";
import type { SuggestedRule, TaskSuggestion } from "../../src/ai/tasks/model.js";
import type { CaseScore } from "./score.js";

/**
 * `npm run eval:review -- <run dir>... [--index=<file>]`
 *
 * Writes `suggestions.md` into each run: every case's suggestions as a reviewer reads them, with
 * the score beside them. With --index, also writes one page linking the runs, for a round.
 */
const EVAL_DIR = path.resolve(__dirname, "..");

type RunResults = {
  config: Record<string, string | number | boolean>;
  summary: {
    gates: { passed: boolean };
    document: {
      recall: number | null;
      intervalAccuracy: number | null;
      citationAccuracy: number | null;
      precision?: number | null;
    };
    runs: number;
    failedRuns: number;
    meanCostMicros: number;
  };
  scores: CaseScore[];
};

function main() {
  const args = process.argv.slice(2);
  const index = args.find((a) => a.startsWith("--index="))?.slice("--index=".length);
  const runs = args.filter((a) => !a.startsWith("--")).map((a) => path.resolve(a));
  if (runs.length === 0) throw new Error("Pass one or more run directories under eval/out/");

  for (const run of runs) {
    writeFileSync(path.join(run, "suggestions.md"), renderRun(run));
    console.log(`wrote ${path.relative(process.cwd(), path.join(run, "suggestions.md"))}`);
  }
  if (index) {
    const file = path.resolve(index);
    writeFileSync(file, renderIndex(runs, path.dirname(file)));
    console.log(`wrote ${path.relative(process.cwd(), file)}`);
  }
}

/** A finished run's results, or what an unfinished one has written so far (its per-case scores). */
function loadRun(run: string): RunResults & { partial: boolean } {
  const file = path.join(run, "results.json");
  if (existsSync(file)) return { ...(JSON.parse(readFileSync(file, "utf8")) as RunResults), partial: false };
  const [, fast = "?", strong = "?"] = /_([^_]+)_([^_]+?)(?:_replay)?$/.exec(path.basename(run)) ?? [];
  const casesDir = path.join(run, "cases");
  const scores = existsSync(casesDir)
    ? readdirSync(casesDir)
        .filter((id) => existsSync(path.join(casesDir, id, "score.json")))
        .sort()
        .map((id) => JSON.parse(readFileSync(path.join(casesDir, id, "score.json"), "utf8")) as CaseScore)
    : [];
  return {
    partial: true,
    config: { fast, strong, recallTier: "?" },
    summary: {
      gates: { passed: scores.every((sc) => Object.values(sc.gates).every((v) => (Array.isArray(v) ? v.length === 0 : v))) },
      document: { recall: null, intervalAccuracy: null, citationAccuracy: null },
      runs: scores.length,
      failedRuns: scores.filter((sc) => sc.status === "failed").length,
      meanCostMicros: scores.length ? scores.reduce((a, sc) => a + sc.costMicros, 0) / scores.length : 0,
    },
    scores,
  };
}

function renderRun(run: string): string {
  const results = loadRun(run);
  const c = results.config;
  const lines = [
    `# ${c.fast} + ${c.strong}, recall on ${c.recallTier}`,
    "",
    ...(results.partial ? [`> **Run still in progress:** ${results.scores.length} cases so far. Re-run eval:review when it finishes for the full scoring.`, ""] : []),
    Object.entries(c).map(([k, v]) => `\`${k}=${v}\``).join(" · "),
    "",
    `Hard gates: **${results.summary.gates.passed ? "pass" : "FAIL"}** · document recall ${pct(results.summary.document.recall)} · intervals ${pct(results.summary.document.intervalAccuracy)} · citations ${pct(results.summary.document.citationAccuracy)} · precision ${pct(results.summary.document.precision ?? null)} · mean cost ${dollars(results.summary.meanCostMicros)} per run · full scoring in [report.md](report.md)`,
    "",
  ];

  for (const score of results.scores) {
    const caseDir = path.join(run, "cases", score.caseId);
    const outcome = existsSync(path.join(caseDir, "result.json"))
      ? (JSON.parse(readFileSync(path.join(caseDir, "result.json"), "utf8")) as PipelineOutcome | null)
      : null;
    lines.push(`## ${score.caseId}`, "");
    const status = score.status === "failed" ? `failed: ${score.errorCode}` : score.status;
    const measures =
      score.kind === "document"
        ? ` · recall ${pct(score.recall)} (${score.matched}/${score.expected}) · intervals ${pct(score.intervalAccuracy)} · citations ${pct(score.citationAccuracy)} · precision ${pct(score.precision ?? null)}`
        : "";
    lines.push(`**${status}** · ${score.suggestions} suggestions${measures} · ${dollars(score.costMicros)} · ${(score.latencyMs / 1000).toFixed(1)} s`, "");

    const documents = outcome?.result.documents ?? [];
    if (documents.length > 0) {
      lines.push(
        "Documents: " +
          documents
            .map((d) => `${d.title}${d.revision ? ` rev ${d.revision}` : ""} (${d.docType.replace(/_/g, " ")}${d.matchesThing ? "" : ", NOT for this Thing"})`)
            .join(" · "),
        "",
      );
    }

    const suggestions = outcome?.result.suggestions ?? [];
    if (suggestions.length > 0) {
      const matchOf = new Map(score.matches.map((m) => [m.suggestion, m]));
      lines.push(
        "| # | Suggestion | Component | Schedule | First due | Source | Pre-selected | Matched expected |",
        "|---|---|---|---|---|---|---|---|",
      );
      suggestions.forEach((s, i) => {
        const m = matchOf.get(s.title);
        const matched = m ? `${m.expected}${m.intervalOk ? "" : " (interval differs)"}${m.citationOk === false ? " (wrong page)" : ""}` : "—";
        lines.push(
          `| ${i + 1} | ${cell(s.title)}${s.matchesExistingTaskId ? " *(already tracked)*" : ""} | ${cell(component(s))} | ${cell(schedule(s))} | ${cell(firstDue(s))} | ${cell(source(s))} | ${s.preselect ? "yes" : "no"} | ${cell(matched)} |`,
        );
      });
      lines.push("");
      const details = suggestions.filter((s) => s.rationale || s.description);
      if (details.length > 0) {
        lines.push("<details><summary>Rationale and description</summary>", "");
        details.forEach((s) => {
          lines.push(`**${s.title}.** ${s.rationale}${s.description ? `\n\n${s.description}` : ""}`, "");
        });
        lines.push("</details>", "");
      }
    }

    const notes = [
      ...score.missed.map((t) => `Missed: ${t}`),
      ...(score.duplicates ?? []).map((t) => `Duplicate of a matched task: ${t}`),
      ...score.invented.map((t) => `Not in the answer key: ${t}`),
      ...score.forbidden.map((t) => `Must not appear: ${t}`),
      ...(score.statusOk === false ? [`Ended ${score.status}, which the case does not expect`] : []),
    ];
    if (notes.length > 0) lines.push(...notes.map((n) => `- ${n}`), "");
  }
  return lines.join("\n");
}

function renderIndex(runs: string[], from: string): string {
  const lines = ["# Bake-off runs", "", "| Run | Gates | Doc recall | Intervals | Citations | Precision | Failed | Mean cost |", "|---|---|---|---|---|---|---|---|"];
  for (const run of runs) {
    const r = loadRun(run);
    const name = `${r.config.fast} + ${r.config.strong}, recall ${r.config.recallTier}`;
    const link = path.relative(from, path.join(run, "suggestions.md"));
    const d = r.summary.document;
    lines.push(
      `| [${name}](${link}) | ${r.summary.gates.passed ? "pass" : "FAIL"} | ${pct(d.recall)} | ${pct(d.intervalAccuracy)} | ${pct(d.citationAccuracy)} | ${pct(d.precision ?? null)} | ${r.summary.failedRuns}/${r.summary.runs} | ${dollars(r.summary.meanCostMicros)} |`,
    );
  }
  return lines.join("\n") + "\n";
}

function component(s: TaskSuggestion): string {
  if (!s.componentSlotKey) return "Thing";
  return s.componentHint ? `${s.componentSlotKey} (${s.componentHint})` : s.componentSlotKey;
}

function schedule(s: TaskSuggestion): string {
  if (s.rules.length === 0) return s.isOneTime ? "once" : "on condition";
  const parts = s.rules.map(ruleText);
  return (s.isOneTime ? "once; " : "") + parts.join(" or ");
}

function ruleText(r: SuggestedRule): string {
  switch (r.kind) {
    case "time":
      return `every ${r.every} ${r.unit}`;
    case "meter":
      return `every ${r.interval.toLocaleString("en-US")} ${r.meterKey.replace(/_/g, " ")}`;
    case "seasonal":
      return `in months ${r.months.join(", ")}`;
    case "on_condition":
      return `on condition${r.description ? ` (${r.description})` : ""}`;
  }
}

function firstDue(s: TaskSuggestion): string {
  if (!s.firstDue) return s.lastDone ? `last done ${s.lastDone.date}` : "—";
  const parts = [
    s.firstDue.meter ? `${s.firstDue.meter.value.toLocaleString("en-US")} ${s.firstDue.meter.meterKey.replace(/_/g, " ")}` : null,
    s.firstDue.date,
  ].filter(Boolean);
  return parts.join(" or ");
}

function source(s: TaskSuggestion): string {
  const kind = s.sourceKind.replace(/_/g, " ");
  const cite = [s.citation, s.pageRef, s.sourcePages.length ? `PDF p. ${s.sourcePages.join(", ")}` : ""].filter(Boolean).join(", ");
  const reg = s.type !== "routine" ? ` · ${s.type.replace(/_/g, " ").toUpperCase()} ${s.referenceNumber}` : "";
  return `${kind}${cite ? `: ${cite}` : ""}${reg}`;
}

function cell(s: string): string {
  return s.replace(/\|/g, "\\|").replace(/\n/g, " ");
}

function pct(v: number | null): string {
  return v === null ? "—" : `${Math.round(v * 100)}%`;
}

function dollars(micros: number): string {
  return `$${(micros / 1_000_000).toFixed(3)}`;
}

if (require.main === module) {
  try {
    main();
  } catch (e) {
    console.error(e);
    process.exit(1);
  }
}

export { EVAL_DIR, renderRun, renderIndex };
