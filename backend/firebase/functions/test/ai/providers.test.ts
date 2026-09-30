import type Anthropic from "@anthropic-ai/sdk";
import type { AnthropicVertex } from "@anthropic-ai/vertex-sdk";
import { FinishReason, type GoogleGenAI } from "@google/genai";
import type OpenAI from "openai";
import { describe, expect, it } from "vitest";

import { AiError } from "../../src/ai/errors.js";
import { createClaudeProvider } from "../../src/ai/providers/claudeProvider.js";
import { createGeminiProvider } from "../../src/ai/providers/geminiProvider.js";
import { generateValidated } from "../../src/ai/providers/generateValidated.js";
import { createOpenAiProvider } from "../../src/ai/providers/openAiProvider.js";
import { assertPortableSchema } from "../../src/ai/providers/portableSchema.js";
import { createProvider, PROVIDER_CANDIDATES } from "../../src/ai/providers/registry.js";
import {
  AiOutputParseError,
  type AiGenerateRequest,
  type AiProvider,
  type JsonSchema,
} from "../../src/ai/providers/types.js";

const SCHEMA: JsonSchema = {
  type: "object",
  properties: { title: { type: "string" }, hours: { type: ["number", "null"] } },
  required: ["title", "hours"],
  additionalProperties: false,
};

const PDF = new Uint8Array([0x25, 0x50, 0x44, 0x46]);
const PNG = new Uint8Array([0x89, 0x50]);

function request(tier: "fast" | "strong" = "strong"): AiGenerateRequest {
  return {
    system: "Extract the schedule.",
    parts: [{ pdfBytes: PDF }, { image: PNG, mime: "image/png" }, { text: "Go." }],
    schema: SCHEMA,
    tier,
    maxOutputTokens: 1000,
  };
}

async function rejection(p: Promise<unknown>): Promise<unknown> {
  return p.then(
    () => expect.fail("expected a rejection"),
    (e: unknown) => e,
  );
}

describe("claude provider", () => {
  function fakeClient(message: Partial<Anthropic.Message> | Error) {
    const calls: Record<string, unknown>[] = [];
    const client = {
      messages: {
        stream(params: Record<string, unknown>) {
          calls.push(params);
          return {
            finalMessage: async () => {
              if (message instanceof Error) throw message;
              return {
                model: params.model,
                stop_reason: "end_turn",
                usage: { input_tokens: 1000, output_tokens: 200 },
                content: [{ type: "text", text: '{"title":"Oil","hours":50}' }],
                ...message,
              };
            },
          };
        },
      },
    };
    return { client: client as unknown as AnthropicVertex, calls };
  }

  it("sends parts, schema and tier effort, and prices the usage", async () => {
    const { client, calls } = fakeClient({});
    const provider = createClaudeProvider({ model: "claude-sonnet-5-5", channel: "vertex", client });

    const out = await provider.generate(request("fast"));

    expect(out.json).toEqual({ title: "Oil", hours: 50 });
    // $2 / $10 per MTok.
    expect(out.usage).toEqual({ inputTokens: 1000, outputTokens: 200, costMicros: 4000 });
    const params = calls[0];
    expect(params.output_config).toEqual({
      format: { type: "json_schema", schema: SCHEMA },
      effort: "low",
    });
    expect(params.model).toBe("claude-sonnet-5-5");
    const content = (params.messages as Array<{ content: Array<Record<string, unknown>> }>)[0].content;
    expect(content.map((c) => c.type)).toEqual(["document", "image", "text"]);
    expect(content[0].source).toEqual({
      type: "base64",
      media_type: "application/pdf",
      data: Buffer.from(PDF).toString("base64"),
    });
  });

  it("asks the direct API for Haiku by its plain id", async () => {
    const { client, calls } = fakeClient({});
    await createClaudeProvider({ model: "claude-haiku-4-5", channel: "direct", client }).generate(request());
    expect(calls[0].model).toBe("claude-haiku-4-5");
  });

  it("asks Vertex for the Haiku snapshot, with no effort", async () => {
    const { client, calls } = fakeClient({});
    const out = await createClaudeProvider({ model: "claude-haiku-4-5", channel: "vertex", client }).generate(
      request(),
    );

    expect(calls[0].model).toBe("claude-haiku-4-5@20251001");
    expect(calls[0].output_config).toEqual({ format: { type: "json_schema", schema: SCHEMA } });
    // $1 / $5 per MTok.
    expect(out.usage.costMicros).toBe(2000);
  });

  it("reports a refusal as a provider error that keeps its usage", async () => {
    const { client } = fakeClient({ stop_reason: "refusal", content: [] });
    const e = await rejection(
      createClaudeProvider({ model: "claude-sonnet-5-5", channel: "vertex", client }).generate(request()),
    );
    expect(e).toBeInstanceOf(AiError);
    expect((e as AiError).code).toBe("provider_error");
    expect((e as AiError).usage?.costMicros).toBe(4000);
  });

  it("reports truncated or non-JSON output as a parse error", async () => {
    const truncated = fakeClient({ stop_reason: "max_tokens" });
    const prose = fakeClient({ content: [{ type: "text", text: "Sure! Here", citations: null }] });
    for (const { client } of [truncated, prose]) {
      const e = await rejection(
        createClaudeProvider({ model: "claude-sonnet-5-5", channel: "vertex", client }).generate(request()),
      );
      expect(e).toBeInstanceOf(AiOutputParseError);
    }
  });

  it("wraps an SDK error as a provider error", async () => {
    const { client } = fakeClient(new Error("overloaded"));
    const e = await rejection(
      createClaudeProvider({ model: "claude-sonnet-5-5", channel: "vertex", client }).generate(request()),
    );
    expect((e as AiError).code).toBe("provider_error");
  });
});

describe("gemini provider", () => {
  function fakeClient(response: Record<string, unknown>) {
    const calls: Record<string, unknown>[] = [];
    const client = {
      models: {
        async generateContent(params: Record<string, unknown>) {
          calls.push(params);
          return {
            text: '{"title":"Oil","hours":50}',
            candidates: [{ finishReason: FinishReason.STOP }],
            usageMetadata: { promptTokenCount: 1000, candidatesTokenCount: 100, thoughtsTokenCount: 100 },
            ...response,
          };
        },
      },
    };
    return { client: client as unknown as GoogleGenAI, calls };
  }

  it("sends the schema and thinking level, and bills thoughts as output", async () => {
    const { client, calls } = fakeClient({});
    const out = await createGeminiProvider({ model: "gemini-3.8-flash", client }).generate(
      request("strong"),
    );

    expect(out.json).toEqual({ title: "Oil", hours: 50 });
    // $0.75 / $3.75 per MTok: 750 + 750.
    expect(out.usage).toEqual({ inputTokens: 1000, outputTokens: 200, costMicros: 1500 });
    const config = calls[0].config as Record<string, unknown>;
    expect(config.responseJsonSchema).toBe(SCHEMA);
    expect(config.responseMimeType).toBe("application/json");
    expect(config.thinkingConfig).toEqual({ thinkingLevel: "HIGH" });
    const parts = (calls[0].contents as Array<{ parts: Array<Record<string, unknown>> }>)[0].parts;
    expect(parts[0]).toEqual({
      inlineData: { mimeType: "application/pdf", data: Buffer.from(PDF).toString("base64") },
    });
  });

  it("treats MAX_TOKENS as a parse error and SAFETY as a provider error", async () => {
    const truncated = fakeClient({ candidates: [{ finishReason: FinishReason.MAX_TOKENS }] });
    expect(
      await rejection(createGeminiProvider({ model: "gemini-3.8-flash", client: truncated.client }).generate(request())),
    ).toBeInstanceOf(AiOutputParseError);

    const blocked = fakeClient({ candidates: [{ finishReason: FinishReason.SAFETY }] });
    const e = await rejection(
      createGeminiProvider({ model: "gemini-3.8-flash", client: blocked.client }).generate(request()),
    );
    expect((e as AiError).code).toBe("provider_error");
  });
});

describe("openai provider", () => {
  function fakeClient(response: Record<string, unknown>) {
    const calls: Record<string, unknown>[] = [];
    const client = {
      responses: {
        async create(params: Record<string, unknown>) {
          calls.push(params);
          return {
            status: "completed",
            output: [],
            output_text: '{"title":"Oil","hours":50}',
            usage: { input_tokens: 1000, output_tokens: 200 },
            ...response,
          };
        },
      },
    };
    return { client: client as unknown as OpenAI, calls };
  }

  it("sends a strict schema, files as data URLs, and the tier effort", async () => {
    const { client, calls } = fakeClient({});
    const out = await createOpenAiProvider({ model: "gpt-6-sol", client }).generate(request("fast"));

    expect(out.json).toEqual({ title: "Oil", hours: 50 });
    expect(out.usage.costMicros).toBe(4000);
    expect(calls[0].text).toEqual({
      format: { type: "json_schema", name: "output", schema: SCHEMA, strict: true },
    });
    expect(calls[0].reasoning).toEqual({ effort: "low" });
    const content = (calls[0].input as Array<{ content: Array<Record<string, unknown>> }>)[0].content;
    expect(content[0]).toEqual({
      type: "input_file",
      filename: "document-1.pdf",
      file_data: `data:application/pdf;base64,${Buffer.from(PDF).toString("base64")}`,
    });
    expect(content[1].type).toBe("input_image");
  });

  it("treats an incomplete response as a parse error and a refusal as a provider error", async () => {
    const incomplete = fakeClient({ status: "incomplete", incomplete_details: { reason: "max_output_tokens" } });
    expect(
      await rejection(createOpenAiProvider({ model: "gpt-6-sol", client: incomplete.client }).generate(request())),
    ).toBeInstanceOf(AiOutputParseError);

    const refused = fakeClient({
      output: [{ type: "message", content: [{ type: "refusal", refusal: "no" }] }],
    });
    const e = await rejection(
      createOpenAiProvider({ model: "gpt-6-sol", client: refused.client }).generate(request()),
    );
    expect((e as AiError).code).toBe("provider_error");
  });
});

describe("generateValidated", () => {
  const usage = { inputTokens: 10, outputTokens: 5, costMicros: 100 };

  function scripted(...answers: Array<unknown | Error>): AiProvider & { requests: AiGenerateRequest[] } {
    const requests: AiGenerateRequest[] = [];
    return {
      id: "scripted",
      requests,
      async generate(req) {
        requests.push(req);
        const answer = answers[requests.length - 1];
        if (answer instanceof Error) throw answer;
        return { json: answer, usage };
      },
    };
  }

  it("returns valid output on the first attempt", async () => {
    const out = await generateValidated(scripted({ title: "Oil", hours: null }), request());
    expect(out).toEqual({ json: { title: "Oil", hours: null }, usage, attempts: 1 });
  });

  it("retries once with the validation error appended and sums the usage", async () => {
    const provider = scripted({ title: "Oil" }, { title: "Oil", hours: 50 });
    const out = await generateValidated(provider, request());

    expect(out.attempts).toBe(2);
    expect(out.usage.costMicros).toBe(200);
    const retryParts = provider.requests[1].parts;
    expect(retryParts).toHaveLength(request().parts.length + 1);
    expect((retryParts.at(-1) as { text: string }).text).toContain("hours");
  });

  it("retries a parse error too", async () => {
    const out = await generateValidated(
      scripted(new AiOutputParseError("not JSON", usage), { title: "Oil", hours: 1 }),
      request(),
    );
    expect(out.attempts).toBe(2);
    expect(out.usage.costMicros).toBe(200);
  });

  it("fails invalid_output after the second bad answer, keeping what was spent", async () => {
    const e = await rejection(generateValidated(scripted({}, { title: 3, hours: 1 }), request()));
    expect((e as AiError).code).toBe("invalid_output");
    expect((e as AiError).usage?.costMicros).toBe(200);
  });

  it("passes a provider error through with the usage spent so far", async () => {
    const e = await rejection(
      generateValidated(scripted({}, new AiError("provider_error", "down")), request()),
    );
    expect((e as AiError).code).toBe("provider_error");
    expect((e as AiError).usage?.costMicros).toBe(100);
  });
});

describe("assertPortableSchema", () => {
  it("accepts a closed schema with null unions", () => {
    expect(() =>
      assertPortableSchema({
        type: "object",
        properties: {
          items: { type: "array", items: SCHEMA },
          kind: { anyOf: [{ type: "string", enum: ["a", "b"] }, { type: "null" }] },
        },
        required: ["items", "kind"],
        additionalProperties: false,
      }),
    ).not.toThrow();
  });

  it("rejects open objects, optional properties and unsupported keywords", () => {
    expect(() => assertPortableSchema({ ...SCHEMA, additionalProperties: true })).toThrow(/additionalProperties/);
    expect(() => assertPortableSchema({ ...SCHEMA, required: ["title"] })).toThrow(/hours/);
    expect(() =>
      assertPortableSchema({
        type: "object",
        properties: { n: { type: "integer", minimum: 0 } },
        required: ["n"],
        additionalProperties: false,
      }),
    ).toThrow(/\$\.n: keyword "minimum"/);
  });
});

describe("registry", () => {
  it("constructs every candidate", () => {
    const credentials = { vertex: { project: "p", location: "global" }, openAiApiKey: "k" };
    for (const candidate of PROVIDER_CANDIDATES) {
      expect(createProvider(candidate.id, credentials).id).toBe(candidate.id);
    }
  });

  it("refuses a model with no price", () => {
    expect(() => createGeminiProvider({ model: "gemini-9", apiKey: "k" })).toThrow(/No price/);
    expect(() => createProvider("gpt-unknown")).toThrow(/Unknown provider/);
    expect(() => createProvider("claude-sonnet-5-5")).toThrow(/Vertex needs a project/);
    expect(createProvider("claude-sonnet-5-5", { claudeChannel: "direct", anthropicApiKey: "k" }).id).toBe("claude-sonnet-5-5");
  });
});
