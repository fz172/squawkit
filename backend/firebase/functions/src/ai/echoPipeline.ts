import type { AiPipeline } from "./worker.js";

/**
 * The developer round trip (AI_JOB_KIND_ECHO; phase A's exit, design §15). It reports one stage and
 * succeeds with the request's own bytes, so a client proves the whole path (start, enqueue,
 * worker, stage update, result, listener, close) byte for byte without a model call or spend.
 */
export const echoPipeline: AiPipeline = {
  async run(request, context) {
    context.reportStage("echoing");
    return { status: "succeeded", result: request };
  },
};
