import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { parseArgs } from "node:util";

import { createDocumentAiOcr } from "../../src/ai/document/ocr/documentAiOcr.js";
import type { AiTier } from "../../src/ai/providers/types.js";
import { createProvider, PROVIDER_CANDIDATES } from "../../src/ai/providers/registry.js";
import type { LocateMethod } from "../../src/ai/tasks/locate.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { loadCases } from "./caseFormat.js";
import { replayProvider, type RecordedCall } from "./recording.js";
import { renderReport, summarize } from "./report.js";
import { runCase, type RunConfig } from "./runCase.js";
import type { CaseScore } from "./score.js";

/**
 * `npm run eval:tasks -- --fast=<id> --strong=<id> [--cases=a,b] [--locate=keywords|model|all]
 *   [--recall-tier=fast|strong] [--ocr=document-ai|none] [--repeat=N] [--warm]
 *   [--claude=vertex|direct] [--replay=<run dir>]`
 *
 * Costs money unless --replay is given. See eval/README.md.
 */
const EVAL_DIR = path.resolve(__dirname, "..");

async function main() {
  const { values } = parseArgs({
    options: {
      fast: { type: "string" },
      strong: { type: "string" },
      cases: { type: "string", default: "all" },
      locate: { type: "string", default: "keywords" },
      "recall-tier": { type: "string", default: "fast" },
      ocr: { type: "string", default: "document-ai" },
      repeat: { type: "string", default: "1" },
      warm: { type: "boolean", default: false },
      replay: { type: "string" },
      claude: { type: "string", default: "vertex" },
    },
  });

  const replayDir = values.replay ? path.resolve(values.replay) : null;
  const previous = replayDir ? (JSON.parse(readFileSync(path.join(replayDir, "results.json"), "utf8")) as Results) : null;
  const fastId = values.fast ?? previous?.config.fast;
  const strongId = values.strong ?? previous?.config.strong;
  if (!fastId || !strongId) {
    throw new Error(`--fast and --strong are required. Candidates: ${PROVIDER_CANDIDATES.map((c) => c.id).join(", ")}`);
  }
  const locate = oneOf<LocateMethod>(values.locate, ["keywords", "model", "all"], "--locate");
  const recallTier = oneOf<AiTier>(values["recall-tier"], ["fast", "strong"], "--recall-tier");
  const repeat = replayDir ? 1 : Math.max(1, Number(values.repeat));
  const claudeChannel = oneOf<"vertex" | "direct">(values.claude, ["vertex", "direct"], "--claude");

  const cases = loadCases(path.join(EVAL_DIR, "cases"), values.cases === "all" ? "all" : values.cases!.split(","));
  const credentials = {
    vertex: {
      project: process.env.VERTEX_PROJECT ?? "wingslog-9ca4e",
      location: process.env.VERTEX_LOCATION ?? "global",
    },
    claudeChannel,
  };
  const processor = process.env.DOCUMENT_AI_PROCESSOR;
  if (values.ocr === "document-ai" && !processor && !replayDir) {
    console.warn("DOCUMENT_AI_PROCESSOR is not set: image-only pages will not be read.");
  }

  const runId = `${new Date().toISOString().replace(/[:.]/g, "-")}_${fastId}_${strongId}${replayDir ? "_replay" : ""}`;
  const outDir = path.join(EVAL_DIR, "out", runId);
  mkdirSync(outDir, { recursive: true });

  const scores: CaseScore[] = [];
  const warmScores: CaseScore[] = [];
  for (const loaded of cases) {
    const id = loaded.evalCase.id;
    const recorded = replayDir ? readRecording(replayDir, id) : null;
    const config: RunConfig = {
      fast: recorded ? replayProvider(fastId, recorded) : createProvider(fastId, credentials),
      strong: recorded ? replayProvider(strongId, recorded) : createProvider(strongId, credentials),
      ocr: values.ocr === "document-ai" && processor ? createDocumentAiOcr({ processorName: processor }) : undefined,
      locate,
      recallTier,
      docsDir: path.join(EVAL_DIR, "docs"),
      warm: values.warm && !replayDir,
    };
    for (let i = 0; i < repeat; i++) {
      process.stdout.write(`${id}${repeat > 1 ? ` #${i + 1}` : ""} … `);
      const run = await runCase(loaded, config);
      scores.push(run.score);
      if (run.warmScore) warmScores.push(run.warmScore);
      console.log(`${run.score.status}${run.score.errorCode ? ` (${run.score.errorCode})` : ""}, ${run.score.suggestions} suggestions, $${(run.score.costMicros / 1e6).toFixed(3)}, ${(run.score.latencyMs / 1000).toFixed(1)} s`);

      const caseDir = path.join(outDir, "cases", i === 0 ? id : `${id}#${i + 1}`);
      mkdirSync(caseDir, { recursive: true });
      writeFileSync(path.join(caseDir, "result.json"), JSON.stringify(run.outcome, null, 2));
      writeFileSync(path.join(caseDir, "score.json"), JSON.stringify(run.score, null, 2));
      writeFileSync(path.join(caseDir, "recording.json"), JSON.stringify(run.recording));
    }
  }

  const documentCounts = new Map(cases.map((c) => [c.evalCase.id, c.evalCase.request.documents.length]));
  const summary = summarize(scores, warmScores, documentCounts);
  const config = {
    fast: fastId,
    strong: strongId,
    locate,
    recallTier,
    claude: claudeChannel,
    ocr: ocrLabel(values.ocr, processor),
    repeat,
    generationVersion: GENERATION_VERSION,
    ...(replayDir ? { replayOf: path.basename(replayDir) } : {}),
  };
  const results: Results = { config, summary, scores };
  writeFileSync(path.join(outDir, "results.json"), JSON.stringify(results, null, 2));
  writeFileSync(path.join(outDir, "report.md"), renderReport(config, summary, scores));
  console.log(`\nHard gates ${summary.gates.passed ? "pass" : "FAIL"}; report: ${path.relative(process.cwd(), path.join(outDir, "report.md"))}`);
}

type Results = {
  config: { fast: string; strong: string } & Record<string, string | number | boolean>;
  summary: ReturnType<typeof summarize>;
  scores: CaseScore[];
};

function readRecording(runDir: string, caseId: string): RecordedCall[] {
  const file = path.join(runDir, "cases", caseId, "recording.json");
  if (!existsSync(file)) throw new Error(`No recording for ${caseId} in ${runDir}`);
  return JSON.parse(readFileSync(file, "utf8")) as RecordedCall[];
}

function oneOf<T extends string>(value: string | undefined, allowed: T[], flag: string): T {
  if (value && (allowed as string[]).includes(value)) return value as T;
  throw new Error(`${flag} must be one of ${allowed.join(", ")}`);
}

function ocrLabel(ocr: string | undefined, processor: string | undefined): string {
  return ocr === "document-ai" && processor ? "document-ai" : "none";
}

main().catch((e: unknown) => {
  console.error(e);
  process.exit(1);
});
