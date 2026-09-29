/**
 * List prices, US dollars per million tokens, as published on 2026-09-28. A dollar per million
 * tokens is one micro-dollar per token, so cost in micros is tokens × price.
 *
 * A model missing here cannot be constructed, so the bake-off never reports a free run.
 */
type ModelPrice = { model: string; inputPerMTok: number; outputPerMTok: number };

const PRICES: ModelPrice[] = [
  { model: "claude-opus-5-5", inputPerMTok: 4, outputPerMTok: 20 },
  { model: "claude-sonnet-5-5", inputPerMTok: 2, outputPerMTok: 10 },
  { model: "claude-haiku-4-5", inputPerMTok: 1, outputPerMTok: 5 },
  // Introductory price; it doubles on 2027-01-01.
  { model: "gemini-3.8-flash", inputPerMTok: 0.75, outputPerMTok: 3.75 },
  { model: "gemini-3.5-flash-lite", inputPerMTok: 0.3, outputPerMTok: 2.5 },
  { model: "gemini-3.1-pro-preview", inputPerMTok: 2, outputPerMTok: 12 },
  { model: "gpt-6-sol", inputPerMTok: 2, outputPerMTok: 10 },
  { model: "gpt-6-luna", inputPerMTok: 0.1, outputPerMTok: 0.5 },
  { model: "gpt-5.4-mini", inputPerMTok: 0.75, outputPerMTok: 4.5 },
];

export function priceFor(model: string): ModelPrice {
  const price = PRICES.find((p) => p.model === model);
  if (!price) throw new Error(`No price for model ${model}`);
  return price;
}

export function costMicros(model: string, inputTokens: number, outputTokens: number): number {
  const price = priceFor(model);
  return Math.round(inputTokens * price.inputPerMTok + outputTokens * price.outputPerMTok);
}

export function hasPrice(model: string): boolean {
  return PRICES.some((p) => p.model === model);
}
