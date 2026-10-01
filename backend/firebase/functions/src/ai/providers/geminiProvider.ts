import { FinishReason, GoogleGenAI, ThinkingLevel, type Part } from "@google/genai";
import { Agent, fetch as undiciFetch } from "undici";

import { AiError } from "../errors.js";
import { describe, parseJson } from "./json.js";
import { costMicros, priceFor } from "./pricing.js";
import {
  AiOutputParseError,
  MIME_PDF,
  type AiGenerateRequest,
  type AiGenerateResponse,
  type AiPart,
  type AiProvider,
  type AiTier,
} from "./types.js";

/**
 * MEDIUM, not HIGH, for the strong tier: at HIGH the tailor spent 20–32k thinking tokens on a
 * no-document bike, hit the output cap and failed with 500s on 3 of 4 attempts (bake-off round 2).
 */
const THINKING_BY_TIER: Record<AiTier, ThinkingLevel> = {
  fast: ThinkingLevel.LOW,
  strong: ThinkingLevel.MEDIUM,
};

/**
 * A tailor call at MEDIUM thinking can run past Node's default 5-minute headers timeout, which
 * surfaces as "fetch failed" (bake-off round 3). Gemini gets its own pool that waits 10 minutes.
 */
export const GEMINI_TIMEOUT_MS = 10 * 60 * 1000;
const dispatcher = new Agent({ headersTimeout: GEMINI_TIMEOUT_MS, bodyTimeout: GEMINI_TIMEOUT_MS });

/** Connection failures worth another try. A timeout is not one: the model is just slow. */
const DROPPED_CONNECTION = new Set(["UND_ERR_SOCKET", "ECONNRESET", "EPIPE", "UND_ERR_CONNECT_TIMEOUT", "ECONNREFUSED"]);
const FETCH_ATTEMPTS = 3;

export async function geminiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  for (let attempt = 1; ; attempt++) {
    try {
      return (await undiciFetch(input as Parameters<typeof undiciFetch>[0], {
        ...(init as Parameters<typeof undiciFetch>[1]),
        dispatcher,
      })) as unknown as Response;
    } catch (e) {
      if (attempt >= FETCH_ATTEMPTS || init?.signal?.aborted || !isDroppedConnection(e)) throw e;
      await new Promise((r) => setTimeout(r, 2000 * attempt));
    }
  }
}

export function isDroppedConnection(e: unknown): boolean {
  const code = (e as { cause?: { code?: string } })?.cause?.code;
  return code !== undefined && DROPPED_CONNECTION.has(code);
}

/**
 * The SDK retries 408, 429 and 5xx with exponential backoff when asked; three attempts matches
 * the Anthropic SDK's default of two retries. Vertex returns transient 500s under load.
 */
export const GEMINI_HTTP_OPTIONS = {
  retryOptions: { attempts: 3, initialDelay: 2 },
  timeout: GEMINI_TIMEOUT_MS,
  fetch: geminiFetch,
};

export type GeminiProviderOptions = {
  model: string;
  /** Vertex AI in a GCP project, authenticated by ADC. The production path. */
  vertex?: { project: string; location: string };
  /** The Gemini Developer API, for local runs without GCP credentials. */
  apiKey?: string;
  client?: GoogleGenAI;
};

export function createGeminiProvider(options: GeminiProviderOptions): AiProvider {
  priceFor(options.model);
  const client = options.client ?? newClient(options);

  return {
    id: options.model,
    async generate(req: AiGenerateRequest): Promise<AiGenerateResponse> {
      let response;
      try {
        response = await client.models.generateContent({
          model: options.model,
          contents: [{ role: "user", parts: toParts(req.parts) }],
          config: {
            systemInstruction: req.system,
            responseMimeType: "application/json",
            responseJsonSchema: req.schema,
            maxOutputTokens: req.maxOutputTokens,
            thinkingConfig: { thinkingLevel: THINKING_BY_TIER[req.tier] },
          },
        });
      } catch (e) {
        throw new AiError("provider_error", `${options.model}: ${describe(e)}`, { cause: e });
      }

      const meta = response.usageMetadata;
      const inputTokens = (meta?.promptTokenCount ?? 0) + (meta?.toolUsePromptTokenCount ?? 0);
      // Thinking tokens are billed as output.
      const outputTokens = (meta?.candidatesTokenCount ?? 0) + (meta?.thoughtsTokenCount ?? 0);
      const usage = {
        inputTokens,
        outputTokens,
        costMicros: costMicros(options.model, inputTokens, outputTokens),
      };

      const finish = response.candidates?.[0]?.finishReason;
      if (finish === FinishReason.MAX_TOKENS) {
        throw new AiOutputParseError(`${options.model}: output hit max tokens`, usage);
      }
      if (finish !== undefined && finish !== FinishReason.STOP) {
        throw new AiError("provider_error", `${options.model}: finished with ${finish}`, { usage });
      }
      return { json: parseJson(response.text ?? "", options.model, usage), usage };
    },
  };
}

function newClient(options: GeminiProviderOptions): GoogleGenAI {
  if (options.vertex) {
    return new GoogleGenAI({
      vertexai: true,
      project: options.vertex.project,
      location: options.vertex.location,
      httpOptions: GEMINI_HTTP_OPTIONS,
    });
  }
  if (options.apiKey) return new GoogleGenAI({ apiKey: options.apiKey, httpOptions: GEMINI_HTTP_OPTIONS });
  throw new Error("Gemini needs either vertex or apiKey");
}

function toParts(parts: AiPart[]): Part[] {
  return parts.map((part): Part => {
    if ("text" in part) return { text: part.text };
    if ("pdfBytes" in part) {
      return { inlineData: { mimeType: MIME_PDF, data: Buffer.from(part.pdfBytes).toString("base64") } };
    }
    return { inlineData: { mimeType: part.mime, data: Buffer.from(part.image).toString("base64") } };
  });
}
