import { PDFDocument, StandardFonts } from "pdf-lib";
import { describe, expect, it } from "vitest";

import { AiError } from "../../src/ai/errors.js";
import type { AiGenerateRequest, AiProvider, JsonSchema } from "../../src/ai/providers/types.js";
import { assertPortableSchema } from "../../src/ai/providers/portableSchema.js";
import { InMemoryPipelineCache } from "../../src/ai/tasks/cache.js";
import { identityHash, normalizeIdentity } from "../../src/ai/tasks/identity.js";
import { locateByKeywords, looksTabular, withContext } from "../../src/ai/tasks/locate.js";
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
      const raw = answer(req);
      if (raw instanceof Error) throw raw;
      // A scripted tailor says nothing about the curated list unless the test does.
      const json = stage === "tailor" ? { notApplicable: [], ...(raw as object) } : raw;
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
        originKind: "ai_thing",
      }),
    ]);
    expect(h.stages.map(([s]) => s)).toEqual(["recalling_schedule", "tailoring", "validating"]);
    expect(h.calls.map((c) => [c.stage, c.provider, c.tier])).toEqual([
      ["recall", "fast-model", "fast"],
      ["tailor", "strong-model", "strong"],
    ]);
    expect(h.cache.keys()).toEqual([expect.stringMatching(new RegExp(`^id/${GENERATION_VERSION}/[0-9a-f]{64}$`))]);
  });

  it("passes on the curated items the tailor says do not fit, in range and once each", async () => {
    const h = harness({
      recall: () => RECALLED,
      tailor: (): TailorOutput => ({
        suggestions: [tailored({})],
        documents: [],
        // The context's starter pack has two items; 5 is out of range.
        notApplicable: [
          { index: 0, reason: "battery-electric: no engine oil" },
          { index: 5, reason: "invented" },
          { index: 0, reason: "repeated" },
        ],
      }),
    });

    const out = await runTaskPipeline(request(), h.deps);

    expect(out.curatedNotApplicable).toEqual([0]);
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

  it("asks whether the run is still open before the tailor, and does not tailor when it is not", async () => {
    const h = harness({ recall: () => RECALLED, tailor: () => expect.fail("tailored a closed run") });
    const closed = new Error("closed");
    const e = await rejection(runTaskPipeline(request(), { ...h.deps, checkOpen: () => Promise.reject(closed) }));
    expect(e).toBe(closed);
    expect(h.stages.map(([s]) => s)).toEqual(["recalling_schedule"]);
    expect(h.calls.map((c) => c.stage)).toEqual(["recall"]);
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
      originKind: "ai_document",
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
    expect(h.cache.keys()).toContain(`doc/${GENERATION_VERSION}/sha-blob-ad`);
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

  it("attaches only the located pages that look like tables, and says which", async () => {
    const pages = Array.from({ length: 60 }, (_, i) =>
      i === 44
        ? "Maintenance schedule: spark plugs X X X X replace every 200 hours"
        : i === 45
          ? "Maintenance schedule continued: coolant every 2 years"
          : `Chapter text page ${i + 1}`,
    );
    const h = harness(
      {
        locate: () => ({ pages: [45] }),
        recall: () => RECALLED,
        extract: () => ({ ...adExtraction(), items: [{ ...adExtraction().items[0], pages: [45] }] }),
        tailor: () => ({ suggestions: [tailored({ candidateIds: ["d0.0"] })], documents: [] }),
      },
      { "blob-mm": await pdf(pages) },
    );
    h.deps.attachPdf = true;
    h.deps.locate = "model";

    await runTaskPipeline(request([ref("blob-mm")]), h.deps);

    const parts = h.strong.asked.find((a) => a.stage === "extract")!.req.parts;
    const attached = parts.find((p): p is { pdfBytes: Uint8Array } => "pdfBytes" in p);
    expect(attached).toBeDefined();
    expect((await PDFDocument.load(attached!.pdfBytes)).getPageCount()).toBe(1);
    const text = parts.find((p): p is { text: string } => "text" in p)!.text;
    expect(text).toContain("document pages 45.");
    // Every located page still goes as text.
    expect(text).toContain("=== page 46 ===");
  });

  it("attaches no PDF when no located page looks like a table", async () => {
    const h = harness(
      { recall: () => RECALLED, extract: () => adExtraction(), tailor: () => ({ suggestions: [], documents: [] }) },
      { "blob-ad": await pdf(AD_PAGES) },
    );
    h.deps.attachPdf = true;
    await runTaskPipeline(request([ref("blob-ad")]), h.deps);
    expect(h.strong.asked.find((a) => a.stage === "extract")!.req.parts.some((p) => "pdfBytes" in p)).toBe(false);
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

  it("tells a flattened table from a list", () => {
    expect(looksTabular("Spark plugs X X X(1 X  Coolant • • •")).toBe(true);
    expect(looksTabular("Inspect brake pads. Inspect wiper blades. Rotate tires.")).toBe(false);
    expect(looksTabular("Exhaust X-ray, Xenon lamp, box, x86")).toBe(false);
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
