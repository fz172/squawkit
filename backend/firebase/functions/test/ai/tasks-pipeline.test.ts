import { PDFDocument, StandardFonts } from "pdf-lib";
import { describe, expect, it } from "vitest";

import { AiError } from "../../src/ai/errors.js";
import type { AiGenerateRequest, AiProvider, JsonSchema } from "../../src/ai/providers/types.js";
import { assertPortableSchema } from "../../src/ai/providers/portableSchema.js";
import { InMemoryPipelineCache } from "../../src/ai/tasks/cache.js";
import { identityHash, normalizeIdentity } from "../../src/ai/tasks/identity.js";
import { locateByKeywords, withContext } from "../../src/ai/tasks/locate.js";
import type { SourceDocumentRef, SuggestTasksRequest } from "../../src/ai/tasks/model.js";
import {
  runTaskPipeline,
  type PipelineCallRecord,
  type PipelineDeps,
  type PipelineStage,
} from "../../src/ai/tasks/pipeline.js";
import { EXTRACT_SCHEMA, LOCATE_SCHEMA, RECALL_SCHEMA, TAILOR_SCHEMA } from "../../src/ai/tasks/schemas.js";
import type {
  ExtractOutput,
  FlatRule,
  RecallOutput,
  TailorOutput,
  TailoredSuggestion,
} from "../../src/ai/tasks/stageTypes.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { airplaneContext, page } from "./fixtures.js";

const usage = { inputTokens: 100, outputTokens: 10, costMicros: 50 };

type Answers = {
  locate?: (req: AiGenerateRequest) => unknown;
  extract?: (req: AiGenerateRequest) => unknown;
  recall?: (req: AiGenerateRequest) => unknown;
  tailor?: (req: AiGenerateRequest) => unknown;
};

/** Answers by which stage's schema it is asked for, and remembers what it was asked. */
function scripted(id: string, answers: Answers) {
  const asked: Array<{ stage: string; req: AiGenerateRequest }> = [];
  const stages: Array<[JsonSchema, keyof Answers]> = [
    [LOCATE_SCHEMA, "locate"],
    [EXTRACT_SCHEMA, "extract"],
    [RECALL_SCHEMA, "recall"],
    [TAILOR_SCHEMA, "tailor"],
  ];
  const provider: AiProvider = {
    id,
    async generate(req) {
      const stage = stages.find(([schema]) => schema === req.schema)![1];
      asked.push({ stage, req });
      const answer = answers[stage];
      if (!answer) throw new Error(`${id} was not scripted for ${stage}`);
      const json = answer(req);
      if (json instanceof Error) throw json;
      return { json, usage };
    },
  };
  return { provider, asked };
}

const RULE: FlatRule = {
  kind: "meter",
  every: null,
  unit: null,
  meterKey: "engine_hours",
  interval: 200,
  months: null,
  dayOfMonth: null,
  description: null,
};

function tailored(over: Partial<TailoredSuggestion>): TailoredSuggestion {
  return {
    candidateIds: ["r0"],
    title: "Replace spark plugs",
    rationale: "Rotax recommends it.",
    description: "",
    componentSlotKey: "engine",
    componentHint: null,
    rules: [RULE],
    isOneTime: false,
    firstDue: [],
    lastDoneLogId: null,
    matchesExistingTaskId: null,
    intervalDifferenceNote: null,
    mergesStaticIndex: null,
    confidence: "high",
    ...over,
  };
}

const RECALLED: RecallOutput = {
  identityConfidence: "high",
  items: [
    {
      title: "Spark plug replacement",
      description: "",
      checklist: [],
      intervals: [{ value: 200, unit: "hours" }],
      isOneTime: false,
      componentHint: "engine",
      sourceKind: "common_practice",
      publication: null,
    },
  ],
};

const AD_NUMBER = "AD 2024-05-07";

function adExtraction(overrides: Partial<ExtractOutput> = {}): ExtractOutput {
  return {
    document: {
      manufacturer: "FAA",
      models: ["915 iS"],
      title: "Airworthiness Directive 2024-05-07",
      revision: null,
      docType: "airworthiness_directive",
      referenceNumber: AD_NUMBER,
    },
    items: [
      {
        title: "Inspect fuel pump connector",
        description: "",
        checklist: [],
        intervals: [{ value: 100, unit: "hours" }],
        isOneTime: false,
        pages: [2],
        printedPageRef: null,
        componentHint: "engine",
        type: "airworthiness_directive",
        referenceNumber: AD_NUMBER,
        complianceAuthority: "FAA",
      },
    ],
    ...overrides,
  };
}

async function pdf(pages: string[]): Promise<Uint8Array> {
  const doc = await PDFDocument.create();
  const font = await doc.embedFont(StandardFonts.Helvetica);
  for (const text of pages) doc.addPage().drawText(text, { x: 40, y: 700, font, size: 10 });
  return doc.save();
}

const AD_PAGES = [
  `AIRWORTHINESS DIRECTIVE ${AD_NUMBER} for Rotax 915 iS engines`,
  "Inspect the fuel pump connector every 100 hours of operation.",
];

function ref(blobId: string, sha256 = `sha-${blobId}`): SourceDocumentRef {
  return { blobId, name: `${blobId}.pdf`, mimeType: "application/pdf", sha256, sizeBytes: 1 };
}

function request(documents: SourceDocumentRef[] = []): SuggestTasksRequest {
  return { thingId: "thing-1", context: airplaneContext(), documents };
}

function harness(answers: Answers, blobs: Record<string, Uint8Array> = {}, cache = new InMemoryPipelineCache()) {
  const fast = scripted("fast-model", answers);
  const strong = scripted("strong-model", answers);
  const stages: Array<[PipelineStage, string | undefined]> = [];
  const calls: PipelineCallRecord[] = [];
  const deps: PipelineDeps = {
    fast: fast.provider,
    strong: strong.provider,
    cache,
    async loadDocument(r) {
      const bytes = blobs[r.blobId];
      if (!bytes) throw new Error("no such blob");
      return bytes;
    },
    onStage: (stage, arg) => stages.push([stage, arg]),
    onCall: (record) => calls.push(record),
  };
  return { deps, fast, strong, stages, calls, cache };
}

async function rejection(p: Promise<unknown>): Promise<AiError> {
  return p.then(
    () => expect.fail("expected a rejection"),
    (e: unknown) => e as AiError,
  );
}

describe("task pipeline without documents", () => {
  it("recalls, tailors and validates into a result", async () => {
    const h = harness({
      recall: () => RECALLED,
      tailor: (): TailorOutput => ({
        suggestions: [
          tailored({ lastDoneLogId: "log-1" }),
          tailored({ candidateIds: ["x9"], title: "Invented" }),
          tailored({ candidateIds: ["r0"], title: "Doubtful", confidence: "low" }),
        ],
        documents: [],
      }),
    });

    const out = await runTaskPipeline(request(), h.deps);

    expect(out.status).toBe("succeeded");
    expect(out.result.generationVersion).toBe(GENERATION_VERSION);
    expect(out.result.suggestions).toEqual([
      expect.objectContaining({
        suggestionId: "s1",
        title: "Replace spark plugs",
        sourceKind: "common_practice",
        type: "routine",
        rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }],
        lastDone: { logId: "log-1", date: "2026-05-02", reading: { meterKey: "engine_hours", value: 380 } },
        // Common practice is never pre-selected on an airplane (R27).
        preselect: false,
      }),
    ]);
    expect(h.stages.map(([s]) => s)).toEqual(["recalling_schedule", "tailoring", "validating"]);
    expect(h.calls.map((c) => [c.stage, c.provider, c.tier])).toEqual([
      ["recall", "fast-model", "fast"],
      ["tailor", "strong-model", "strong"],
    ]);
    expect(h.cache.keys()).toEqual([expect.stringMatching(new RegExp(`^id:[0-9a-f]{64}:${GENERATION_VERSION}$`))]);
  });

  it("recalls on the strong model when asked to", async () => {
    const h = harness({ recall: () => RECALLED, tailor: () => ({ suggestions: [tailored({})], documents: [] }) });
    h.deps.recallTier = "strong";
    await runTaskPipeline(request(), h.deps);

    expect(h.fast.asked).toHaveLength(0);
    expect(h.calls.map((c) => [c.stage, c.provider, c.tier])).toEqual([
      ["recall", "strong-model", "strong"],
      ["tailor", "strong-model", "strong"],
    ]);
  });

  it("keeps identifying specs out of the recall prompt", async () => {
    const h = harness({ recall: () => RECALLED, tailor: () => ({ suggestions: [tailored({})], documents: [] }) });
    await runTaskPipeline(request(), h.deps);
    const recallText = JSON.stringify(h.fast.asked[0].req.parts);
    expect(recallText).toContain("sling");
    expect(recallText).not.toContain("N123AB");
  });

  it("returns empty on low identity confidence without tailoring or caching", async () => {
    const h = harness({ recall: () => ({ identityConfidence: "low", items: RECALLED.items }) });
    const out = await runTaskPipeline(request(), h.deps);

    expect(out).toMatchObject({ status: "empty", reason: "low_identity_confidence" });
    expect(h.strong.asked).toHaveLength(0);
    expect(h.cache.keys()).toEqual([]);
  });

  it("returns empty when nothing survives validation", async () => {
    const h = harness({
      recall: () => RECALLED,
      tailor: () => ({ suggestions: [tailored({ candidateIds: [] })], documents: [] }),
    });
    expect(await runTaskPipeline(request(), h.deps)).toMatchObject({ status: "empty", reason: "nothing_survived" });
    expect(h.cache.keys()).toEqual([]);
  });

  it("caches nothing when the tailor fails", async () => {
    const h = harness({ recall: () => RECALLED, tailor: () => ({ wrong: true }) });
    const e = await rejection(runTaskPipeline(request(), h.deps));
    expect(e.code).toBe("invalid_output");
    expect(h.cache.keys()).toEqual([]);
    // The failed tailor is still in the cost log, both attempts.
    expect(h.calls.at(-1)).toMatchObject({ stage: "tailor", usage: { costMicros: 100 } });
  });
});

describe("task pipeline with documents", () => {
  it("keeps a directive typed when the directive prints its number", async () => {
    const h = harness(
      {
        recall: () => RECALLED,
        extract: () => adExtraction(),
        tailor: (): TailorOutput => ({
          suggestions: [tailored({ candidateIds: ["d0.0", "r0"], title: "Inspect fuel pump connector" })],
          documents: [{ index: 0, matchesThing: true }],
        }),
      },
      { "blob-ad": await pdf(AD_PAGES) },
    );

    const out = await runTaskPipeline(request([ref("blob-ad")]), h.deps);

    expect(out.status).toBe("succeeded");
    expect(out.result.suggestions[0]).toMatchObject({
      type: "airworthiness_directive",
      referenceNumber: AD_NUMBER,
      complianceAuthority: "FAA",
      sourceKind: "document",
      citation: "Airworthiness Directive 2024-05-07",
      pageRef: "p. 2",
      sourceDocument: "blob-ad",
      preselect: true,
    });
    expect(out.result.documents).toEqual([
      {
        blobId: "blob-ad",
        name: "blob-ad.pdf",
        manufacturer: "FAA",
        title: "Airworthiness Directive 2024-05-07",
        revision: "",
        docType: "airworthiness_directive",
        matchesThing: true,
      },
    ]);
    // The extraction saw the page markers the citation check relies on.
    expect(JSON.stringify(h.strong.asked.find((a) => a.stage === "extract")!.req.parts)).toContain("=== page 2 ===");
    expect(h.cache.keys()).toContain(`doc:sha-blob-ad:${GENERATION_VERSION}`);
    expect(h.stages).toContainEqual(["reading_document", "blob-ad.pdf"]);
  });

  it("downgrades a directive the document does not print, and drops an uncited item", async () => {
    const h = harness(
      {
        recall: () => RECALLED,
        extract: () =>
          adExtraction({
            items: [
              { ...adExtraction().items[0], referenceNumber: "AD 2019-01-01" },
              { ...adExtraction().items[0], title: "Overhaul the gearbox", pages: [1], intervals: [{ value: 1200, unit: "hours" }] },
            ],
          }),
        tailor: () => ({
          suggestions: [
            tailored({ candidateIds: ["d0.0"], title: "Inspect fuel pump connector" }),
            tailored({ candidateIds: ["d0.1"], title: "Overhaul the gearbox" }),
          ],
          documents: [{ index: 0, matchesThing: false }],
        }),
      },
      { "blob-ad": await pdf(AD_PAGES) },
    );

    const out = await runTaskPipeline(request([ref("blob-ad")]), h.deps);

    expect(out.result.suggestions).toHaveLength(1);
    expect(out.result.suggestions[0]).toMatchObject({ type: "routine", referenceNumber: "", preselect: false });
    expect(out.result.documents[0].matchesThing).toBe(false);
  });

  it("serves both cacheable stages from the cache on a second run", async () => {
    const cache = new InMemoryPipelineCache();
    const answers: Answers = {
      recall: () => RECALLED,
      extract: () => adExtraction(),
      tailor: () => ({ suggestions: [tailored({ candidateIds: ["d0.0"], title: "Inspect fuel pump connector" })], documents: [] }),
    };
    const blobs = { "blob-ad": await pdf(AD_PAGES) };
    await runTaskPipeline(request([ref("blob-ad")]), harness(answers, blobs, cache).deps);

    const second = harness(answers, blobs, cache);
    const out = await runTaskPipeline(request([ref("blob-ad")]), second.deps);

    expect(out.status).toBe("succeeded");
    expect([...second.fast.asked, ...second.strong.asked].map((a) => a.stage)).toEqual(["tailor"]);
    expect(second.calls.filter((c) => c.cacheHit).map((c) => c.stage).sort()).toEqual(["extract", "recall"]);
  });

  it("fails no_schedule_found when no document yields an item", async () => {
    const h = harness(
      { recall: () => RECALLED, extract: () => adExtraction({ items: [] }) },
      { "blob-ad": await pdf(AD_PAGES) },
    );
    expect((await rejection(runTaskPipeline(request([ref("blob-ad")]), h.deps))).code).toBe("no_schedule_found");
  });

  it("fails document_missing for a blob that is not there", async () => {
    const h = harness({ recall: () => RECALLED });
    expect((await rejection(runTaskPipeline(request([ref("blob-gone")]), h.deps))).code).toBe("document_missing");
  });

  it("attaches the located pages as a PDF when asked, and says which pages they are", async () => {
    const pages = Array.from({ length: 60 }, (_, i) =>
      i === 44 ? "Maintenance schedule: replace spark plugs every 200 hours" : `Chapter text page ${i + 1}`,
    );
    const h = harness(
      {
        recall: () => RECALLED,
        extract: () => ({ ...adExtraction(), items: [{ ...adExtraction().items[0], pages: [45] }] }),
        tailor: () => ({ suggestions: [tailored({ candidateIds: ["d0.0"] })], documents: [] }),
      },
      { "blob-mm": await pdf(pages) },
    );
    h.deps.attachPdf = true;

    await runTaskPipeline(request([ref("blob-mm")]), h.deps);

    const parts = h.strong.asked.find((a) => a.stage === "extract")!.req.parts;
    const attached = parts.find((p): p is { pdfBytes: Uint8Array } => "pdfBytes" in p);
    expect(attached).toBeDefined();
    const sliced = await PDFDocument.load(attached!.pdfBytes);
    const text = parts.find((p): p is { text: string } => "text" in p)!.text;
    const markers = [...text.matchAll(/=== page (\d+) ===/g)].map((m) => Number(m[1]));
    expect(sliced.getPageCount()).toBe(markers.length);
    expect(text).toContain(`document pages ${markers.join(", ")}`);
  });

  it("sends no PDF unless asked", async () => {
    const h = harness(
      { recall: () => RECALLED, extract: () => adExtraction(), tailor: () => ({ suggestions: [], documents: [] }) },
      { "blob-ad": await pdf(AD_PAGES) },
    );
    await runTaskPipeline(request([ref("blob-ad")]), h.deps);
    expect(h.strong.asked.find((a) => a.stage === "extract")!.req.parts.some((p) => "pdfBytes" in p)).toBe(false);
  });

  it("sends a long document's located pages only, with the model locator", async () => {
    const pages = Array.from({ length: 60 }, (_, i) =>
      i === 44 ? "Maintenance schedule: replace spark plugs every 200 hours" : `Chapter text page ${i + 1}`,
    );
    const h = harness(
      {
        locate: () => ({ pages: [45, 999] }),
        recall: () => RECALLED,
        extract: () => ({ ...adExtraction(), items: [{ ...adExtraction().items[0], pages: [45] }] }),
        tailor: () => ({ suggestions: [tailored({ candidateIds: ["d0.0"] })], documents: [] }),
      },
      { "blob-mm": await pdf(pages) },
    );
    h.deps.locate = "model";

    await runTaskPipeline(request([ref("blob-mm")]), h.deps);

    const sent = JSON.stringify(h.strong.asked.find((a) => a.stage === "extract")!.req.parts);
    const markers = [...sent.matchAll(/=== page (\d+) ===/g)].map((m) => Number(m[1]));
    expect(markers).toEqual([1, 2, 44, 45, 46]);
    expect(h.calls.find((c) => c.stage === "locate")).toMatchObject({ tier: "fast", pages: 60 });
  });
});

describe("pipeline support", () => {
  it("keeps every stage schema portable", () => {
    for (const schema of [LOCATE_SCHEMA, EXTRACT_SCHEMA, RECALL_SCHEMA, TAILOR_SCHEMA]) {
      expect(() => assertPortableSchema(schema)).not.toThrow();
    }
  });

  it("normalizes identity without identifying specs, whatever the order", () => {
    const a = airplaneContext();
    const b = airplaneContext({
      specs: [
        { key: "model", label: "Model", value: " tsi " },
        { key: "make", label: "Make", value: "SLING" },
        { key: "tail_number", label: "Tail", value: "N999ZZ" },
      ],
    });
    expect(normalizeIdentity(a).specs).toEqual([
      { key: "make", value: "sling" },
      { key: "model", value: "tsi" },
    ]);
    expect(identityHash(normalizeIdentity(a))).toBe(identityHash(normalizeIdentity(b)));
  });

  it("locates schedule pages by keywords and adds context pages", () => {
    const pages = [
      page(1, "Title"),
      page(2, "Introduction"),
      page(3, "Maintenance schedule: inspect every 100 hours, replace filter every 50 hours, check annual"),
      page(4, "Wiring diagram"),
      page(5, "Index"),
    ];
    expect(locateByKeywords(pages)).toEqual([3]);
    expect(withContext([3], 5)).toEqual([1, 2, 3, 4]);
    expect(withContext([], 1)).toEqual([1]);
  });
});
