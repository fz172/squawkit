import Anthropic from "@anthropic-ai/sdk";

import { AiError } from "../errors.js";
import { describe, parseJson } from "./json.js";
import { costMicros, hasPrice, priceFor } from "./pricing.js";
import {
  AiOutputParseError,
  type AiGenerateRequest,
  type AiGenerateResponse,
  type AiPart,
  type AiProvider,
  type AiTier,
} from "./types.js";

type Effort = "low" | "medium" | "high";

type ModelTraits = {
  /** Adaptive thinking with an effort level. Haiku 4.5 takes neither. */
  effort: boolean;
  /** Server-side refusal fallback (`fallbacks: "default"`). */
  fallbacks: boolean;
};

const TRAITS: Record<string, ModelTraits> = {
  "claude-opus-5-5": { effort: true, fallbacks: true },
  "claude-sonnet-5-5": { effort: true, fallbacks: true },
  "claude-haiku-4-5": { effort: false, fallbacks: false },
};

const EFFORT_BY_TIER: Record<AiTier, Effort> = { fast: "low", strong: "high" };

const FALLBACK_BETA = "server-side-fallback-2026-07-01";

export type AnthropicProviderOptions = {
  model: string;
  /** Defaults to the environment's credentials (`ANTHROPIC_API_KEY` or an `ant auth` profile). */
  apiKey?: string;
  client?: Anthropic;
};

export function createAnthropicProvider(options: AnthropicProviderOptions): AiProvider {
  const traits = TRAITS[options.model];
  if (!traits) throw new Error(`Unknown Anthropic model ${options.model}`);
  priceFor(options.model);
  const client = options.client ?? new Anthropic(options.apiKey ? { apiKey: options.apiKey } : {});

  return {
    id: options.model,
    async generate(req: AiGenerateRequest): Promise<AiGenerateResponse> {
      let message: Anthropic.Beta.BetaMessage;
      try {
        message = await client.beta.messages
          .stream({
            model: options.model,
            max_tokens: req.maxOutputTokens,
            system: req.system,
            messages: [{ role: "user", content: toContent(req.parts) }],
            output_config: {
              format: { type: "json_schema", schema: req.schema },
              ...(traits.effort ? { effort: EFFORT_BY_TIER[req.tier] } : {}),
            },
            ...(traits.fallbacks ? { betas: [FALLBACK_BETA], fallbacks: "default" as const } : {}),
          })
          .finalMessage();
      } catch (e) {
        throw new AiError("provider_error", `${options.model}: ${describe(e)}`, { cause: e });
      }

      // A fallback may have served the turn; bill at the model that produced it.
      const billedModel = hasPrice(message.model) ? message.model : options.model;
      const usage = {
        inputTokens: message.usage.input_tokens,
        outputTokens: message.usage.output_tokens,
        costMicros: costMicros(billedModel, message.usage.input_tokens, message.usage.output_tokens),
      };

      if (message.stop_reason === "refusal") {
        throw new AiError("provider_error", `${options.model}: refused`, { usage });
      }
      const text = message.content
        .flatMap((block) => (block.type === "text" ? [block.text] : []))
        .join("");
      if (message.stop_reason === "max_tokens") {
        throw new AiOutputParseError(`${options.model}: output hit max_tokens`, usage);
      }
      return { json: parseJson(text, options.model, usage), usage };
    },
  };
}

function toContent(parts: AiPart[]): Anthropic.Beta.BetaContentBlockParam[] {
  return parts.map((part): Anthropic.Beta.BetaContentBlockParam => {
    if ("text" in part) return { type: "text", text: part.text };
    if ("pdfBytes" in part) {
      return {
        type: "document",
        source: {
          type: "base64",
          media_type: "application/pdf",
          data: Buffer.from(part.pdfBytes).toString("base64"),
        },
      };
    }
    return {
      type: "image",
      source: {
        type: "base64",
        media_type: imageMediaType(part.mime),
        data: Buffer.from(part.image).toString("base64"),
      },
    };
  });
}

function imageMediaType(mime: string): "image/jpeg" | "image/png" | "image/gif" | "image/webp" {
  switch (mime) {
    case "image/jpeg":
    case "image/png":
    case "image/gif":
    case "image/webp":
      return mime;
    default:
      throw new AiError("document_unreadable", `Unsupported image type ${mime}`);
  }
}
