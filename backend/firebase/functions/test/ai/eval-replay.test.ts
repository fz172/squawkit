import { createHash } from "node:crypto";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { PDFDocument, StandardFonts } from "pdf-lib";
import { describe, expect, it } from "vitest";

import { documentPath, type LoadedCase } from "../../eval/src/caseFormat.js";
import { replayProvider } from "../../eval/src/recording.js";
import { runCase, type RunConfig } from "../../eval/src/runCase.js";
import type { AiProvider } from "../../src/ai/providers/types.js";
import { EXTRACT_SCHEMA, RECALL_SCHEMA, TAILOR_SCHEMA } from "../../src/ai/tasks/schemas.js";
import type { ExtractOutput, RecallOutput, TailorOutput } from "../../src/ai/tasks/stageTypes.js";
import { airplaneContext } from "./fixtures.js";

const EXTRACTION: ExtractOutput = {
  document: {
    manufacturer: "Rotax",
    models: ["915 iS"],
    title: "Rotax 915 iS Maintenance Manual",
    revision: "3",
    docType: "maintenance_manual",
    referenceNumber: null,
  },
  items: [
    {
      title: "Replace spark plugs",
      description: "",
      checklist: [],
      intervals: [{ value: 200, unit: "hours" }],
      isOneTime: false,
      pages: [2],
      printedPageRef: "5-12",
      componentHint: "engine",
      type: "routine",
      referenceNumber: null,
      complianceAuthority: null,
    },
  ],
};

const RECALL: RecallOutput = { identityConfidence: "high", items: [] };

const TAILOR: TailorOutput = {
  suggestions: [
    {
      candidateIds: ["d0.0"],
      title: "Replace spark plugs",
      rationale: "Rotax recommends it.",
      description: "",
      componentSlotKey: "engine",
      componentHint: null,
      rules: [
        { kind: "meter", every: null, unit: null, meterKey: "engine_hours", interval: 200, months: null, dayOfMonth: null, description: null },
      ],
      isOneTime: false,
      lastDoneLogId: null,
      matchesExistingTaskId: null,
      intervalDifferenceNote: null,
      mergesStaticIndex: null,
      confidence: "high",
    },
  ],
  documents: [{ index: 0, matchesThing: true }],
};

/** Answers each stage with a fixed output, standing in for a real model. */
function scripted(id: string): AiProvider {
  return {
    id,
    async generate(req) {
      const json =
        req.schema === EXTRACT_SCHEMA ? EXTRACTION : req.schema === RECALL_SCHEMA ? RECALL : req.schema === TAILOR_SCHEMA ? TAILOR : null;
      return { json, usage: { inputTokens: 1000, outputTokens: 100, costMicros: 3000 } };
    },
  };
}

async function synthetic(): Promise<{ loaded: LoadedCase; docsDir: string }> {
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  pdf.addPage().drawText("Rotax 915 iS Maintenance Manual, revision 3", { x: 40, y: 700, font, size: 10 });
  pdf.addPage().drawText("5-12 Scheduled maintenance: replace spark plugs every 200 hours.", { x: 40, y: 700, font, size: 10 });
  const bytes = await pdf.save();
  const sha256 = createHash("sha256").update(bytes).digest("hex");
  const ref = { blobId: "rotax-mm", name: "mm.pdf", mimeType: "application/pdf", sha256, sizeBytes: bytes.length };
  const docsDir = mkdtempSync(path.join(tmpdir(), "eval-docs-"));
  writeFileSync(documentPath(docsDir, ref), bytes);
  return {
    docsDir,
    loaded: {
      evalCase: {
        id: "synthetic",
        description: "",
        kind: "document",
        request: { thingId: "t", context: airplaneContext(), documents: [ref] },
      },
      expected: {
        reviewed: true,
        tasks: [{ titleAliases: ["spark plugs"], rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }], citations: [{ document: "rotax-mm", pages: [2] }] }],
      },
    },
  };
}

describe("eval harness", () => {
  it("records a run, and replaying the recording scores the same with no provider", async () => {
    const { loaded, docsDir } = await synthetic();
    const base: Omit<RunConfig, "fast" | "strong"> = { locate: "keywords", recallTier: "fast", docsDir, warm: true };

    const live = await runCase(loaded, { ...base, fast: scripted("fast-model"), strong: scripted("strong-model") });

    expect(live.score).toMatchObject({ status: "succeeded", recall: 1, intervalAccuracy: 1, citationAccuracy: 1 });
    expect(live.score.gates).toMatchObject({ uncitedDocumentItems: [], validOutput: true });
    expect(live.score.costMicros).toBe(9000);
    // The warm run hits the cache for extract and recall and pays only for the tailor.
    expect(live.warmScore).toMatchObject({ cacheHits: 2, costMicros: 3000 });
    expect(live.recording.map((r) => r.provider).sort()).toEqual(["fast-model", "strong-model", "strong-model", "strong-model"]);

    const replayed = await runCase(loaded, {
      ...base,
      warm: false,
      fast: replayProvider("fast-model", live.recording),
      strong: replayProvider("strong-model", live.recording),
    });
    expect({ ...replayed.score, latencyMs: 0 }).toEqual({ ...live.score, latencyMs: 0 });
    expect(replayed.outcome).toEqual(live.outcome);
  });

  it("refuses to replay a request that was never recorded", async () => {
    const { loaded, docsDir } = await synthetic();
    const changed: LoadedCase = {
      ...loaded,
      evalCase: { ...loaded.evalCase, request: { ...loaded.evalCase.request, context: airplaneContext({ templateId: "boat" }) } },
    };
    await expect(
      runCase(changed, {
        locate: "keywords",
        recallTier: "fast",
        docsDir,
        warm: false,
        fast: replayProvider("fast-model", []),
        strong: replayProvider("strong-model", []),
      }),
    ).rejects.toThrow(/nothing recorded/);
  });
});
