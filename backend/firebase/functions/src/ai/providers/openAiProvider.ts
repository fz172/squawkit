import OpenAI from "openai";
import type { ResponseInputContent } from "openai/resources/responses/responses";

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

const EFFORT_BY_TIER: Record<AiTier, "low" | "high"> = { fast: "low", strong: "high" };

export type OpenAiProviderOptions = {
  model: string;
  /** Defaults to `OPENAI_API_KEY`. */
  apiKey?: string;
  client?: OpenAI;
};

export function createOpenAiProvider(options: OpenAiProviderOptions): AiProvider {
  priceFor(options.model);
  const client = options.client ?? new OpenAI(options.apiKey ? { apiKey: options.apiKey } : {});

  return {
    id: options.model,
    async generate(req: AiGenerateRequest): Promise<AiGenerateResponse> {
      let response: OpenAI.Responses.Response;
      try {
        response = await client.responses.create({
          model: options.model,
          instructions: req.system,
          input: [{ role: "user", content: toContent(req.parts) }],
          // Strict mode is why schemas must be portable (see assertPortableSchema).
          text: { format: { type: "json_schema", name: "output", schema: req.schema, strict: true } },
          max_output_tokens: req.maxOutputTokens,
          reasoning: { effort: EFFORT_BY_TIER[req.tier] },
        });
      } catch (e) {
        throw new AiError("provider_error", `${options.model}: ${describe(e)}`, { cause: e });
      }

      // Reasoning tokens are already inside output_tokens.
      const inputTokens = response.usage?.input_tokens ?? 0;
      const outputTokens = response.usage?.output_tokens ?? 0;
      const usage = {
        inputTokens,
        outputTokens,
        costMicros: costMicros(options.model, inputTokens, outputTokens),
      };

      const refused = response.output.some(
        (item) => item.type === "message" && item.content.some((c) => c.type === "refusal"),
      );
      if (refused) throw new AiError("provider_error", `${options.model}: refused`, { usage });
      if (response.status === "incomplete") {
        const reason = response.incomplete_details?.reason ?? "unknown";
        throw new AiOutputParseError(`${options.model}: incomplete (${reason})`, usage);
      }
      return { json: parseJson(response.output_text, options.model, usage), usage };
    },
  };
}

function toContent(parts: AiPart[]): ResponseInputContent[] {
  return parts.map((part, i): ResponseInputContent => {
    if ("text" in part) return { type: "input_text", text: part.text };
    if ("pdfBytes" in part) {
      return {
        type: "input_file",
        filename: `document-${i + 1}.pdf`,
        file_data: dataUrl(MIME_PDF, part.pdfBytes),
      };
    }
    return { type: "input_image", image_url: dataUrl(part.mime, part.image), detail: "high" };
  });
}

function dataUrl(mime: string, bytes: Uint8Array): string {
  return `data:${mime};base64,${Buffer.from(bytes).toString("base64")}`;
}
