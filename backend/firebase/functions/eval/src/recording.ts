import { createHash } from "node:crypto";

import { AiError, type AiErrorCode } from "../../src/ai/errors.js";
import type { AiGenerateRequest, AiProvider, AiUsage } from "../../src/ai/providers/types.js";
import { AiOutputParseError } from "../../src/ai/providers/types.js";

/**
 * Every provider answer from a run, keyed by its request, so a later run can replay it with no
 * provider at all. This is what lets the scorer run end to end in CI (design §12.4).
 */
export type RecordedCall = {
  key: string;
  provider: string;
  answer:
    | { json: unknown; usage: AiUsage }
    | { parseError: string; usage: AiUsage }
    | { error: AiErrorCode; detail: string };
};

/** The request's identity: prompt, schema, tier and a hash of each binary part. */
export function requestKey(req: AiGenerateRequest): string {
  const parts = req.parts.map((p) =>
    "text" in p ? p.text : "pdfBytes" in p ? `pdf:${sha(p.pdfBytes)}` : `image:${sha(p.image)}`,
  );
  return createHash("sha256")
    .update(JSON.stringify({ system: req.system, schema: req.schema, tier: req.tier, parts }))
    .digest("hex");
}

export function recordingProvider(inner: AiProvider, sink: RecordedCall[]): AiProvider {
  return {
    id: inner.id,
    async generate(req) {
      const key = requestKey(req);
      try {
        const out = await inner.generate(req);
        sink.push({ key, provider: inner.id, answer: out });
        return out;
      } catch (e) {
        if (e instanceof AiOutputParseError) {
          sink.push({ key, provider: inner.id, answer: { parseError: e.message, usage: e.usage } });
        } else if (e instanceof AiError) {
          sink.push({ key, provider: inner.id, answer: { error: e.code, detail: e.detail } });
        }
        throw e;
      }
    },
  };
}

/** Answers each request with what was recorded for it, in order when it was asked twice. */
export function replayProvider(id: string, recorded: RecordedCall[]): AiProvider {
  const queues = new Map<string, RecordedCall[]>();
  for (const call of recorded.filter((c) => c.provider === id)) {
    queues.set(call.key, [...(queues.get(call.key) ?? []), call]);
  }
  return {
    id,
    async generate(req) {
      const next = queues.get(requestKey(req))?.shift();
      if (!next) throw new Error(`${id}: nothing recorded for this request; re-record the run`);
      const { answer } = next;
      if ("json" in answer) return answer;
      if ("parseError" in answer) throw new AiOutputParseError(answer.parseError, answer.usage);
      throw new AiError(answer.error, answer.detail);
    },
  };
}

function sha(bytes: Uint8Array): string {
  return createHash("sha256").update(bytes).digest("hex");
}
