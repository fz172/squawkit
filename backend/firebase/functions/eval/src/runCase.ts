import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";

import type { DocumentPage } from "../../src/ai/document/readDocument.js";
import type { OcrProvider } from "../../src/ai/document/ocr/types.js";
import { AiError } from "../../src/ai/errors.js";
import type { AiProvider, AiTier } from "../../src/ai/providers/types.js";
import { InMemoryPipelineCache } from "../../src/ai/tasks/cache.js";
import type { LocateMethod } from "../../src/ai/tasks/locate.js";
import {
  runTaskPipeline,
  type PipelineCallRecord,
  type PipelineOutcome,
} from "../../src/ai/tasks/pipeline.js";
import { documentPath, type LoadedCase } from "./caseFormat.js";
import { recordingProvider, type RecordedCall } from "./recording.js";
import { scoreCase, type CaseScore } from "./score.js";

export type RunConfig = {
  fast: AiProvider;
  strong: AiProvider;
  ocr?: OcrProvider;
  locate: LocateMethod;
  recallTier: AiTier;
  attachPdf?: boolean;
  docsDir: string;
  /** Run the case a second time on the warm cache, for the R19 cache-hit latency. */
  warm: boolean;
};

export type CaseRun = {
  score: CaseScore;
  warmScore: CaseScore | null;
  outcome: PipelineOutcome | null;
  recording: RecordedCall[];
};

/**
 * One case through the real pipeline, on a fresh cache so no run borrows another's answers (the
 * cache key does not name the provider).
 */
export async function runCase(loaded: LoadedCase, config: RunConfig): Promise<CaseRun> {
  const recording: RecordedCall[] = [];
  const cache = new InMemoryPipelineCache();
  const fast = recordingProvider(config.fast, recording);
  const strong = recordingProvider(config.strong, recording);

  const once = async () => {
    const calls: PipelineCallRecord[] = [];
    const pagesByBlob = new Map<string, DocumentPage[]>();
    const started = Date.now();
    let outcome: PipelineOutcome | { status: "failed"; errorCode: AiError["code"] | null };
    try {
      outcome = await runTaskPipeline(loaded.evalCase.request, {
        fast,
        strong,
        ocr: config.ocr,
        cache,
        locate: config.locate,
        recallTier: config.recallTier,
        attachPdf: config.attachPdf,
        loadDocument: async (ref) => {
          const bytes = new Uint8Array(await readFile(documentPath(config.docsDir, ref)));
          const actual = createHash("sha256").update(bytes).digest("hex");
          if (actual !== ref.sha256) throw new Error(`${ref.name}: sha256 is ${actual}, case says ${ref.sha256}`);
          return bytes;
        },
        onCall: (c) => calls.push(c),
        onDocumentRead: (ref, pages) => pagesByBlob.set(ref.blobId, pages),
      });
    } catch (e) {
      if (!(e instanceof AiError)) throw e;
      outcome = { status: "failed", errorCode: e.code };
    }
    const score = scoreCase({ ...loaded, outcome, pagesByBlob, calls, latencyMs: Date.now() - started });
    return { score, outcome: outcome.status === "failed" ? null : outcome };
  };

  const cold = await once();
  const warm = config.warm && cold.outcome?.status === "succeeded" ? await once() : null;
  return { score: cold.score, warmScore: warm?.score ?? null, outcome: cold.outcome, recording };
}
