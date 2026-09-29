import type { OcrProvider } from "../document/ocr/types.js";
import { readDocument, type DocumentPage } from "../document/readDocument.js";
import { AiError } from "../errors.js";
import { generateValidated } from "../providers/generateValidated.js";
import { describe } from "../providers/json.js";
import type { AiPart, AiProvider, AiTier, AiUsage, JsonSchema } from "../providers/types.js";
import {
  documentCacheKey,
  identityCacheKey,
  type DocumentCacheEntry,
  type IdentityCacheEntry,
  type PipelineCache,
} from "./cache.js";
import { buildDrafts, type CandidateDocument } from "./drafts.js";
import { identityHash, normalizeIdentity } from "./identity.js";
import {
  locateByKeywords,
  pageDigest,
  READ_WHOLE_BELOW_PAGES,
  withContext,
  type LocateMethod,
} from "./locate.js";
import type {
  IdentifiedDocument,
  SourceDocumentRef,
  SuggestTasksRequest,
  SuggestTasksResult,
  TaskSuggestion,
} from "./model.js";
import {
  documentText,
  EXTRACT_SYSTEM,
  LOCATE_SYSTEM,
  RECALL_SYSTEM,
  recallText,
  TAILOR_SYSTEM,
  tailorText,
} from "./prompts.js";
import { EXTRACT_SCHEMA, LOCATE_SCHEMA, RECALL_SCHEMA, TAILOR_SCHEMA } from "./schemas.js";
import type { ExtractOutput, LocateOutput, RecallOutput, TailorOutput } from "./stageTypes.js";
import { validate } from "./validate/validators.js";
import { GENERATION_VERSION } from "./version.js";

/** Progress the worker writes to the job doc for the R19 text; `arg` is a document name. */
export type PipelineStage =
  | "reading_document"
  | "finding_schedule"
  | "extracting_schedule"
  | "recalling_schedule"
  | "tailoring"
  | "validating";

/** One provider, OCR or cache lookup, for the cost log (§5.6). No prompt or document text. */
export type PipelineCallRecord = {
  stage: "ocr" | "locate" | "extract" | "recall" | "tailor";
  provider: string;
  tier: AiTier | null;
  usage: AiUsage;
  latencyMs: number;
  attempts: number;
  cacheHit: boolean;
  pages: number;
};

export type PipelineDeps = {
  fast: AiProvider;
  strong: AiProvider;
  ocr?: OcrProvider;
  cache: PipelineCache;
  /** The worker reads Storage; the eval reads disk. */
  loadDocument(ref: SourceDocumentRef): Promise<Uint8Array>;
  onStage?(stage: PipelineStage, arg?: string): void;
  onCall?(record: PipelineCallRecord): void;
  /** Each document's page text once read, so the eval checks against the same text. */
  onDocumentRead?(ref: SourceDocumentRef, pages: DocumentPage[]): void;
  locate?: LocateMethod;
  /** Today, for first-due dates relative to now. Defaults to the clock. */
  now?: () => Date;
  /**
   * The model recall runs on. Recall is the whole answer on a run without documents, so the
   * bake-off compares both tiers there. Defaults to fast.
   */
  recallTier?: AiTier;
};

export type PipelineOutcome =
  | { status: "succeeded"; result: SuggestTasksResult }
  | { status: "empty"; reason: "low_identity_confidence" | "nothing_survived"; result: SuggestTasksResult };

const MAX_TOKENS = { locate: 2000, extract: 32000, recall: 16000, tailor: 32000 };

type ReadDocument = CandidateDocument & { pages: DocumentPage[]; cached: boolean };

/**
 * The task suggestion pipeline (design §6): read and extract each document and recall the common
 * schedule in parallel, tailor once, validate. Throws AiError for a failed run; returns `empty`
 * when there is nothing confident to say (R21a). Nothing here touches Firebase.
 */
export async function runTaskPipeline(
  request: SuggestTasksRequest,
  deps: PipelineDeps,
): Promise<PipelineOutcome> {
  const [documents, recall] = await Promise.all([
    Promise.all(request.documents.map((ref, index) => readAndExtract(ref, index, deps))),
    recallSchedule(request, deps),
  ]);

  if (documents.length > 0 && documents.every((d) => d.extraction.items.length === 0)) {
    throw new AiError("no_schedule_found", `none of ${documents.length} documents has a schedule`);
  }
  const confident = recall.output.identityConfidence !== "low";
  if (documents.length === 0 && !confident) {
    return { status: "empty", reason: "low_identity_confidence", result: result([], []) };
  }
  const recalled = confident ? recall.output.items : [];
  if (documents.length === 0 && recalled.length === 0) {
    return { status: "empty", reason: "nothing_survived", result: result([], []) };
  }

  deps.onStage?.("tailoring");
  const tailored = (await call(deps, "tailor", deps.strong, {
    system: TAILOR_SYSTEM,
    parts: [{ text: tailorText(request.context, { documents, recalled }) }],
    schema: TAILOR_SCHEMA,
    tier: "strong",
    maxOutputTokens: MAX_TOKENS.tailor,
  })) as TailorOutput;

  deps.onStage?.("validating");
  const matches = (index: number) =>
    tailored.documents.find((d) => d.index === index)?.matchesThing ?? true;
  const drafts = validate(buildDrafts(tailored.suggestions, documents, recalled), {
    context: request.context,
    documents: documents.map((d) => ({
      blobId: d.ref.blobId,
      docType: d.extraction.document.docType,
      pages: d.pages,
      matchesThing: matches(d.index),
    })),
    today: (deps.now?.() ?? new Date()).toISOString().slice(0, 10),
  });

  const identified: IdentifiedDocument[] = documents.map((d) => ({
    blobId: d.ref.blobId,
    name: d.ref.name,
    manufacturer: d.extraction.document.manufacturer,
    title: d.extraction.document.title,
    revision: d.extraction.document.revision ?? "",
    docType: d.extraction.document.docType,
    matchesThing: matches(d.index),
  }));
  if (drafts.length === 0) {
    return { status: "empty", reason: "nothing_survived", result: result([], identified) };
  }

  // Only now, so a failed tailor never caches a bad extraction (§6.5).
  await Promise.all([
    ...documents
      .filter((d) => !d.cached)
      .map((d) => deps.cache.set(documentCacheKey(d.ref.sha256), d.extraction satisfies DocumentCacheEntry)),
    ...(recall.cached ? [] : [deps.cache.set(recall.key, recall.output satisfies IdentityCacheEntry)]),
  ]);

  const suggestions: TaskSuggestion[] = drafts.map(
    ({ rawRules: _raw, rawFirstDue: _due, lastDoneLogId: _log, confidence: _c, evidence: _e, ...s }, i) => ({
      suggestionId: `s${i + 1}`,
      ...s,
    }),
  );
  return { status: "succeeded", result: result(suggestions, identified) };
}

async function readAndExtract(
  ref: SourceDocumentRef,
  index: number,
  deps: PipelineDeps,
): Promise<ReadDocument> {
  deps.onStage?.("reading_document", ref.name);
  let bytes: Uint8Array;
  try {
    bytes = await deps.loadDocument(ref);
  } catch (e) {
    throw new AiError("document_missing", `${ref.blobId}: ${describe(e)}`, { cause: e });
  }
  const started = Date.now();
  const read = await readDocument({ bytes, mime: ref.mimeType }, { ocr: deps.ocr });
  deps.onDocumentRead?.(ref, read.pages);
  if (read.ocr.pagesBilled > 0 && deps.ocr) {
    deps.onCall?.({
      stage: "ocr",
      provider: deps.ocr.id,
      tier: null,
      usage: { inputTokens: 0, outputTokens: 0, costMicros: read.ocr.costMicros },
      latencyMs: Date.now() - started,
      attempts: 1,
      cacheHit: false,
      pages: read.ocr.pagesBilled,
    });
  }

  const key = documentCacheKey(ref.sha256);
  const hit = (await deps.cache.get(key)) as DocumentCacheEntry | undefined;
  if (hit) {
    deps.onCall?.(cacheHitRecord("extract", read.pages.length));
    return { index, ref, pages: read.pages, extraction: hit, cached: true };
  }

  deps.onStage?.("finding_schedule", ref.name);
  const wanted = await locatePages(read.pages, deps);
  const pages = read.pages.filter((p) => wanted.includes(p.n));

  deps.onStage?.("extracting_schedule", ref.name);
  const images: AiPart[] = pages.flatMap((p) => (p.image ? [{ image: p.image, mime: ref.mimeType }] : []));
  const extraction = (await call(
    deps,
    "extract",
    deps.strong,
    {
      system: EXTRACT_SYSTEM,
      parts: [...images, { text: documentText(ref, pages) }],
      schema: EXTRACT_SCHEMA,
      tier: "strong",
      maxOutputTokens: MAX_TOKENS.extract,
    },
    pages.length,
  )) as ExtractOutput;
  return { index, ref, pages: read.pages, extraction, cached: false };
}

async function locatePages(pages: DocumentPage[], deps: PipelineDeps): Promise<number[]> {
  const method = deps.locate ?? "keywords";
  if (method === "all" || pages.length < READ_WHOLE_BELOW_PAGES) return pages.map((p) => p.n);

  let located: number[] = [];
  if (method === "model") {
    const out = (await call(
      deps,
      "locate",
      deps.fast,
      {
        system: LOCATE_SYSTEM,
        parts: [{ text: pageDigest(pages) }],
        schema: LOCATE_SCHEMA,
        tier: "fast",
        maxOutputTokens: MAX_TOKENS.locate,
      },
      pages.length,
    )) as LocateOutput;
    located = out.pages.filter((n) => Number.isInteger(n) && n >= 1 && n <= pages.length);
  }
  if (located.length === 0) located = locateByKeywords(pages);
  return withContext(located, pages.length);
}

async function recallSchedule(request: SuggestTasksRequest, deps: PipelineDeps) {
  const identity = normalizeIdentity(request.context);
  const key = identityCacheKey(identityHash(identity));
  const hit = (await deps.cache.get(key)) as IdentityCacheEntry | undefined;
  if (hit) {
    deps.onCall?.(cacheHitRecord("recall", 0));
    return { key, output: hit, cached: true };
  }

  deps.onStage?.("recalling_schedule");
  const tier = deps.recallTier ?? "fast";
  const output = (await call(deps, "recall", deps[tier], {
    system: RECALL_SYSTEM,
    parts: [{ text: recallText(identity) }],
    schema: RECALL_SCHEMA,
    tier,
    maxOutputTokens: MAX_TOKENS.recall,
  })) as RecallOutput;
  return { key, output, cached: false };
}

async function call(
  deps: PipelineDeps,
  stage: PipelineCallRecord["stage"],
  provider: AiProvider,
  req: { system: string; parts: AiPart[]; schema: JsonSchema; tier: AiTier; maxOutputTokens: number },
  pages = 0,
): Promise<unknown> {
  const started = Date.now();
  const record = (usage: AiUsage, attempts: number) =>
    deps.onCall?.({
      stage,
      provider: provider.id,
      tier: req.tier,
      usage,
      latencyMs: Date.now() - started,
      attempts,
      cacheHit: false,
      pages,
    });
  try {
    const out = await generateValidated(provider, req);
    record(out.usage, out.attempts);
    return out.json;
  } catch (e) {
    if (e instanceof AiError && e.usage) record(e.usage, 0);
    throw e;
  }
}

function cacheHitRecord(stage: "extract" | "recall", pages: number): PipelineCallRecord {
  return {
    stage,
    provider: "cache",
    tier: null,
    usage: { inputTokens: 0, outputTokens: 0, costMicros: 0 },
    latencyMs: 0,
    attempts: 0,
    cacheHit: true,
    pages,
  };
}

function result(suggestions: TaskSuggestion[], documents: IdentifiedDocument[]): SuggestTasksResult {
  return { suggestions, documents, generationVersion: GENERATION_VERSION };
}
