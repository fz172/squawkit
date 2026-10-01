import { FinishReason, GoogleGenAI, ThinkingLevel, type Part } from "@google/genai";

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
 * The SDK retries 408, 429 and 5xx with exponential backoff when asked; three attempts matches
 * the Anthropic SDK's default of two retries. Vertex returns transient 500s under load.
 */
export const GEMINI_HTTP_OPTIONS = { retryOptions: { attempts: 3, initialDelay: 2 } };

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
