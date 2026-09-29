import { createAnthropicProvider } from "./anthropicProvider.js";
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

/** Every field is optional; each SDK falls back to its own environment variable or ADC. */
export type ProviderCredentials = {
  anthropicApiKey?: string;
  openAiApiKey?: string;
  gemini?: { vertex?: { project: string; location: string }; apiKey?: string };
};

export function createProvider(id: string, credentials: ProviderCredentials = {}): AiProvider {
  const candidate = PROVIDER_CANDIDATES.find((c) => c.id === id);
  if (!candidate) throw new Error(`Unknown provider ${id}`);
  switch (candidate.vendor) {
    case "anthropic":
      return createAnthropicProvider({ model: id, apiKey: credentials.anthropicApiKey });
    case "google":
      return createGeminiProvider({ model: id, ...credentials.gemini });
    case "openai":
      return createOpenAiProvider({ model: id, apiKey: credentials.openAiApiKey });
  }
}
