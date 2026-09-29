/**
 * The provider abstraction (design §5.4). One `AiProvider` is one model; the fast/strong pair is
 * chosen by config, so switching provider is a config write once both adapters are deployed.
 *
 * Nothing under `src/ai/providers` imports firebase-functions, so the eval harness runs it as-is.
 */

export type AiTier = "fast" | "strong";

/**
 * A JSON Schema every adapter can send as structured output. See [assertPortableSchema] for the
 * subset: every object closed and every property required, optional values as `null` unions.
 */
export type JsonSchema = Record<string, unknown>;

export type AiPart =
  | { text: string }
  | { pdfBytes: Uint8Array }
  | { image: Uint8Array; mime: string };

export type AiGenerateRequest = {
  system: string;
  parts: AiPart[];
  schema: JsonSchema;
  tier: AiTier;
  maxOutputTokens: number;
};

export type AiUsage = {
  inputTokens: number;
  outputTokens: number;
  costMicros: number;
};

export type AiGenerateResponse = {
  json: unknown;
  usage: AiUsage;
};

export interface AiProvider {
  /** The model id as the provider names it, e.g. "claude-sonnet-5-5". */
  readonly id: string;
  generate(req: AiGenerateRequest): Promise<AiGenerateResponse>;
}

/**
 * The model answered, but not with parseable JSON (truncated, refused into prose). The validating
 * wrapper retries this once like a schema failure; the usage was still spent.
 */
export class AiOutputParseError extends Error {
  constructor(
    detail: string,
    readonly usage: AiUsage,
  ) {
    super(detail);
    this.name = "AiOutputParseError";
  }
}

export const MIME_PDF = "application/pdf";
