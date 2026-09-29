import type Anthropic from "@anthropic-ai/sdk";
import { AnthropicVertex } from "@anthropic-ai/vertex-sdk";

import { AiError } from "../errors.js";
import { describe, parseJson } from "./json.js";
import { costMicros, priceFor } from "./pricing.js";
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
  /** The id Vertex serves it under. Current models keep the bare id; older ones are snapshots. */
  vertexModel: string;
  /** Adaptive thinking with an effort level. Haiku 4.5 takes neither. */
  effort: boolean;
};

const TRAITS: Record<string, ModelTraits> = {
  "claude-opus-5-5": { vertexModel: "claude-opus-5-5", effort: true },
  "claude-sonnet-5-5": { vertexModel: "claude-sonnet-5-5", effort: true },
  "claude-haiku-4-5": { vertexModel: "claude-haiku-4-5@20251001", effort: false },
};

const EFFORT_BY_TIER: Record<AiTier, Effort> = { fast: "low", strong: "high" };

export type ClaudeVertexProviderOptions = {
  model: string;
  /**
   * The GCP project, authenticated by ADC. `global` is priced as Anthropic's list price; a
   * regional endpoint costs 10% more, which the price table does not reflect.
   */
  vertex?: { project: string; location: string };
  client?: AnthropicVertex;
};

/**
 * Claude through Vertex AI, so billing, IAM and data terms stay in the GCP project. Vertex has no
 * server-side refusal fallback; a refusal is a provider error.
 */
export function createClaudeVertexProvider(options: ClaudeVertexProviderOptions): AiProvider {
  const traits = TRAITS[options.model];
  if (!traits) throw new Error(`Unknown Claude model ${options.model}`);
  priceFor(options.model);
  const client = options.client ?? newClient(options);

  return {
    id: options.model,
    async generate(req: AiGenerateRequest): Promise<AiGenerateResponse> {
      let message: Anthropic.Message;
      try {
        message = await client.messages
          .stream({
            model: traits.vertexModel,
            max_tokens: req.maxOutputTokens,
            system: req.system,
            messages: [{ role: "user", content: toContent(req.parts) }],
            output_config: {
              format: { type: "json_schema", schema: req.schema },
              ...(traits.effort ? { effort: EFFORT_BY_TIER[req.tier] } : {}),
            },
          })
          .finalMessage();
      } catch (e) {
        throw new AiError("provider_error", `${options.model}: ${describe(e)}`, { cause: e });
      }

      const usage = {
        inputTokens: message.usage.input_tokens,
        outputTokens: message.usage.output_tokens,
        costMicros: costMicros(options.model, message.usage.input_tokens, message.usage.output_tokens),
      };

      if (message.stop_reason === "refusal") {
        throw new AiError("provider_error", `${options.model}: refused`, { usage });
      }
      if (message.stop_reason === "max_tokens") {
        throw new AiOutputParseError(`${options.model}: output hit max_tokens`, usage);
      }
      const text = message.content
        .flatMap((block) => (block.type === "text" ? [block.text] : []))
        .join("");
      return { json: parseJson(text, options.model, usage), usage };
    },
  };
}

function newClient(options: ClaudeVertexProviderOptions): AnthropicVertex {
  if (!options.vertex) throw new Error("Claude needs a Vertex project");
  return new AnthropicVertex({ projectId: options.vertex.project, region: options.vertex.location });
}

function toContent(parts: AiPart[]): Anthropic.ContentBlockParam[] {
  return parts.map((part): Anthropic.ContentBlockParam => {
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
