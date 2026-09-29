import { AiOutputParseError, type AiUsage } from "./types.js";

export function parseJson(text: string, model: string, usage: AiUsage): unknown {
  try {
    return JSON.parse(text);
  } catch {
    throw new AiOutputParseError(`${model}: output is not JSON`, usage);
  }
}

export function describe(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}
