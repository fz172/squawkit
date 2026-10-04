import { SuggestTasksRequest as SuggestTasksRequestProto } from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { SuggestTasksResult as SuggestTasksResultProto } from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { AiError } from "../errors.js";
import { createProvider } from "../providers/registry.js";
import type { AiProvider } from "../providers/types.js";
import { documentAiProcessor } from "../../config/env.js";
import { createDocumentAiOcr } from "../document/ocr/documentAiOcr.js";
import type { OcrProvider } from "../document/ocr/types.js";
import type { AiJobFinish, AiPipeline } from "../worker.js";
import { ENTITY_SEGMENT_THING } from "../../config/entitySegment.js";
import { readThingLabel } from "../../notifications/onRecordWritten.js";
import { suggestionsPushData } from "../../notifications/pushMessages.js";
import { enabledTokensFor, sendPush } from "../../notifications/pushSender.js";
import { curatedSuggestions } from "./curatedResult.js";
import type { SuggestTasksResult, TaskSuggestion } from "./model.js";
import { runTaskPipeline } from "./pipeline.js";
import { requestFromProto, resultToProto } from "./wire.js";

/**
 * The task-suggestion kind's pipeline, as the worker runs it (design §5.2, §6; T14).
 *
 * The worker hands over the request proto; this decodes it into the pipeline's types, runs the
 * stages on the providers `ai_config/global` names, and encodes the result proto back. Stage
 * updates, cost records and the Firestore cache go through the worker's context.
 *
 * Documents are refused until T21 wires stages 1–2: `kinds.ts` stops them at start, and this
 * refuses them again so no path can run them half-wired.
 */
export function createTaskSuggestionPipeline(
  providerFor: (id: string) => AiProvider = vertexProvider,
  notifyFinished: (finish: AiJobFinish) => Promise<void> = pushFinished,
): AiPipeline {
  return {
    onFinished: notifyFinished,
    async run(request, context) {
      const decoded = requestFromProto(SuggestTasksRequestProto.decode(request));
      if (decoded.documents.length > 0) {
        throw new AiError("provider_error", "document runs are not enabled yet (T21)");
      }
      // The tailor reads the curated list as its starter items, index for index, so a suggestion
      // can say which one it covers (design §6.4, §6.8).
      const curated = curatedSuggestions(decoded.context);
      const staticPack = curated.map(({ title, description, componentSlotKey, rules }) => ({
        title,
        description,
        componentSlotKey,
        rules,
      }));
      const outcome = await runTaskPipeline({ ...decoded, context: { ...decoded.context, staticPack } }, {
        fast: providerFor(context.config.fastProvider),
        strong: providerFor(context.config.strongProvider),
        cache: context.cache,
        loadDocument: async () => {
          throw new AiError("document_missing", "no document runs yet");
        },
        ocr: productionOcr(),
        onStage: (stage, arg) => context.reportStage(stage, arg),
        onCall: (record) => context.recordCall(record),
        // The bake-off's settings (§12.5): keyword locating, table pages as PDF, recall on fast.
        locate: "keywords",
        attachPdf: true,
        recallTier: "fast",
      });
      return {
        status: outcome.status,
        result: SuggestTasksResultProto.encode(resultToProto(withCurated(withoutLogLinks(outcome.result), curated, outcome.curatedNotApplicable))).finish(),
      };
    },
  };
}

/**
 * The result with no suggestion tied to a log (owner's decision, 2026-10-02). Logs go to the model
 * as context and may shape what it suggests, but a suggestion never names one as when it was last
 * done, so a new task's schedule runs from when it is accepted. The tailor prompt still asks for
 * `lastDoneLogId` until its next revision (a GENERATION_VERSION bump and an eval run); this keeps
 * whatever it answers from leaving the server.
 */
export function withoutLogLinks(result: SuggestTasksResult): SuggestTasksResult {
  return { ...result, suggestions: result.suggestions.map((s) => ({ ...s, lastDone: null })) };
}

/**
 * The AI's suggestions, then every curated one no suggestion covers (design §6.8, PRD R25): the
 * screen showed the curated list from the start, and loses only the cards the AI replaced, and
 * those it judged not to fit this Thing ([notApplicable], an engine oil change on an EV). On an
 * EMPTY run with no tailor, that is the whole curated list.
 */
export function withCurated(
  result: SuggestTasksResult,
  curated: TaskSuggestion[],
  notApplicable: number[] = [],
): SuggestTasksResult {
  const covered = new Set(result.suggestions.map((s) => s.mergesStaticIndex).filter((i) => i >= 0));
  const dropped = new Set(notApplicable);
  return {
    ...result,
    suggestions: [...result.suggestions, ...curated.filter((_, i) => !covered.has(i) && !dropped.has(i))],
  };
}

/**
 * The R20 push: the run ended, whatever the outcome, so the person who started it can come back
 * to it. Only a model run gets here; a curated-only job never reaches the worker. A device with no
 * enabled token is simply not told.
 */
export async function pushFinished(finish: AiJobFinish): Promise<void> {
  const targets = await enabledTokensFor(finish.ref.callerUid);
  if (targets.length === 0) return;
  const tailNumber = await readThingLabel(finish.job.hostUid, finish.job.thingId, ENTITY_SEGMENT_THING);
  await sendPush(
    targets,
    suggestionsPushData({ thingId: finish.job.thingId, tailNumber, status: finish.status }),
  );
}

/** Document AI OCR for image-only pages, when the functions config names a processor. */
function productionOcr(): OcrProvider | undefined {
  const processorName = documentAiProcessor();
  return processorName ? createDocumentAiOcr({ processorName }) : undefined;
}

/**
 * Production providers: Gemini (and Claude, if config names it) on Vertex in this project's
 * `global` endpoint, authenticated by the runtime service account (§5.4). No keys.
 */
function vertexProvider(id: string): AiProvider {
  const project = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT ?? "";
  return createProvider(id, { vertex: { project, location: "global" } });
}
