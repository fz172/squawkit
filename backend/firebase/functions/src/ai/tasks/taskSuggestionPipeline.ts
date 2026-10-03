import { SuggestTasksRequest as SuggestTasksRequestProto } from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { SuggestTasksResult as SuggestTasksResultProto } from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { AiError } from "../errors.js";
import { createProvider } from "../providers/registry.js";
import type { AiProvider } from "../providers/types.js";
import type { AiPipeline } from "../worker.js";
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
): AiPipeline {
  return {
    async run(request, context) {
      const decoded = requestFromProto(SuggestTasksRequestProto.decode(request));
      if (decoded.documents.length > 0) {
        throw new AiError("provider_error", "document runs are not enabled yet (T21)");
      }
      const outcome = await runTaskPipeline(decoded, {
        fast: providerFor(context.config.fastProvider),
        strong: providerFor(context.config.strongProvider),
        cache: context.cache,
        loadDocument: async () => {
          throw new AiError("document_missing", "no document runs yet");
        },
        onStage: (stage, arg) => context.reportStage(stage, arg),
        onCall: (record) => context.recordCall(record),
        // The bake-off's settings (§12.5): keyword locating, table pages as PDF, recall on fast.
        locate: "keywords",
        attachPdf: true,
        recallTier: "fast",
      });
      return {
        status: outcome.status,
        result: SuggestTasksResultProto.encode(resultToProto(outcome.result)).finish(),
      };
    },
  };
}

/**
 * Production providers: Gemini (and Claude, if config names it) on Vertex in this project's
 * `global` endpoint, authenticated by the runtime service account (§5.4). No keys.
 */
function vertexProvider(id: string): AiProvider {
  const project = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT ?? "";
  return createProvider(id, { vertex: { project, location: "global" } });
}
