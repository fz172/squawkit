import { createClaudeProvider, type ClaudeChannel } from "./claudeProvider.js";
import { createGeminiProvider } from "./geminiProvider.js";
import { createOpenAiProvider } from "./openAiProvider.js";
import type { AiProvider } from "./types.js";

export type ProviderVendor = "anthropic" | "google" | "openai";

export type ProviderCandidate = { id: string; vendor: ProviderVendor };

/** The bake-off field (PRD §9.2): three vendors, a fast and a strong model or two from each. */
export const PROVIDER_CANDIDATES: ProviderCandidate[] = [
  { id: "claude-opus-5-5", vendor: "anthropic" },
  { id: "claude-sonnet-5-5", vendor: "anthropic" },
  { id: "claude-haiku-4-5", vendor: "anthropic" },
  { id: "gemini-3.1-pro-preview", vendor: "google" },
  { id: "gemini-3.8-flash", vendor: "google" },
  { id: "gemini-3.5-flash-lite", vendor: "google" },
  { id: "gpt-6-sol", vendor: "openai" },
  { id: "gpt-6-luna", vendor: "openai" },
  { id: "gpt-5.4-mini", vendor: "openai" },
];

/**
 * Claude and Gemini run on Vertex AI in `vertex.project`, authenticated by ADC. Claude can use
 * Anthropic's API instead (`claudeChannel: "direct"`, `ANTHROPIC_API_KEY`), and Gemini an API key,
 * for local runs; OpenAI is not on Vertex and defaults to `OPENAI_API_KEY`.
 */
export type ProviderCredentials = {
  vertex?: { project: string; location: string };
  /** Defaults to `vertex`, the production route. */
  claudeChannel?: ClaudeChannel;
  anthropicApiKey?: string;
  geminiApiKey?: string;
  openAiApiKey?: string;
};

export function createProvider(id: string, credentials: ProviderCredentials = {}): AiProvider {
  const candidate = PROVIDER_CANDIDATES.find((c) => c.id === id);
  if (!candidate) throw new Error(`Unknown provider ${id}`);
  switch (candidate.vendor) {
    case "anthropic":
      return createClaudeProvider({
        model: id,
        channel: credentials.claudeChannel ?? "vertex",
        vertex: credentials.vertex,
        apiKey: credentials.anthropicApiKey,
      });
    case "google":
      return createGeminiProvider({ model: id, vertex: credentials.vertex, apiKey: credentials.geminiApiKey });
    case "openai":
      return createOpenAiProvider({ model: id, apiKey: credentials.openAiApiKey });
  }
}
