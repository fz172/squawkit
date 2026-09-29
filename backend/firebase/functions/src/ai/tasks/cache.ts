import type { ExtractOutput, RecallOutput } from "./stageTypes.js";
import { GENERATION_VERSION } from "./version.js";

/**
 * The Thing-independent stage cache (design §6.5, PRD R42–R44). It holds derived items and
 * document metadata only: never document bytes or text, and nothing from the Thing.
 *
 * The worker's Firestore implementation arrives in T09; the eval harness uses the in-memory one.
 */
export interface PipelineCache {
  get(key: string): Promise<unknown | undefined>;
  set(key: string, value: unknown): Promise<void>;
}

export class InMemoryPipelineCache implements PipelineCache {
  private readonly entries = new Map<string, unknown>();

  async get(key: string): Promise<unknown | undefined> {
    return this.entries.get(key);
  }

  async set(key: string, value: unknown): Promise<void> {
    this.entries.set(key, structuredClone(value));
  }

  keys(): string[] {
    return [...this.entries.keys()];
  }
}

/** Stage 2. The revision is inside the content, so the hash already tells revisions apart. */
export function documentCacheKey(sha256: string): string {
  return `doc:${sha256}:${GENERATION_VERSION}`;
}

/** Stage 3. `identityHash` is from identity.ts, which strips identifying specs first. */
export function identityCacheKey(identityHash: string): string {
  return `id:${identityHash}:${GENERATION_VERSION}`;
}

export type DocumentCacheEntry = ExtractOutput;
export type IdentityCacheEntry = RecallOutput;
