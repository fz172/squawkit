import { createClaudeProvider, type ClaudeChannel } from "./claudeProvider.js";
import { createGeminiProvider } from "./geminiProvider.js";
import type { AiProvider } from "./types.js";

export type ProviderVendor = "anthropic" | "google";

export type ProviderCandidate = { id: string; vendor: ProviderVendor };

/** The bake-off field (PRD §9.2): Claude and Gemini, fast and strong models from each. */
export const PROVIDER_CANDIDATES: ProviderCandidate[] = [
  { id: "claude-opus-5-5", vendor: "anthropic" },
  { id: "claude-sonnet-5-5", vendor: "anthropic" },
  { id: "claude-haiku-4-5", vendor: "anthropic" },
  { id: "gemini-3.1-pro-preview", vendor: "google" },
  { id: "gemini-3.8-flash", vendor: "google" },
  { id: "gemini-3.5-flash-lite", vendor: "google" },
];

/**
 * Claude and Gemini run on Vertex AI in `vertex.project`, authenticated by ADC. Claude can use
 * Anthropic's API instead (`claudeChannel: "direct"`, `ANTHROPIC_API_KEY`), and Gemini an API key,
 * for local runs.
 */
export type ProviderCredentials = {
  vertex?: { project: string; location: string };
  /** Defaults to `vertex`, the production route. */
  claudeChannel?: ClaudeChannel;
  anthropicApiKey?: string;
  geminiApiKey?: string;
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
  }
}
