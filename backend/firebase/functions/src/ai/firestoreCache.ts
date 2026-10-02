import { Timestamp } from "firebase-admin/firestore";

import { adminDb } from "../config/firebaseAdmin.js";
import { aiCacheDocPath, type AiCacheDoc } from "./collections.js";
import type { PipelineCache } from "./tasks/cache.js";

/**
 * The worker's stage cache (design §6.5), one document per key under `ai_cache`. Values are stored
 * as JSON text: cache entries are plain data, and a string keeps Firestore from rewriting their
 * shape (it has no `undefined`, and nested arrays are not allowed).
 */
export class FirestorePipelineCache implements PipelineCache {
  constructor(private readonly now: () => Date = () => new Date()) {}

  async get(key: string): Promise<unknown | undefined> {
    const snap = await adminDb.doc(aiCacheDocPath(key)).get();
    const value = (snap.data() as AiCacheDoc | undefined)?.value;
    return value == null ? undefined : JSON.parse(value);
  }

  async set(key: string, value: unknown): Promise<void> {
    const doc: AiCacheDoc = { value: JSON.stringify(value), createdAt: Timestamp.fromDate(this.now()) };
    await adminDb.doc(aiCacheDocPath(key)).set(doc);
  }
}
