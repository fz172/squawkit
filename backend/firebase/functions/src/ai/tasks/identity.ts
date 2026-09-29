import { createHash } from "node:crypto";

import type { SpecValue, SuggestionContext } from "./model.js";

/**
 * Spec keys that identify one Thing rather than describe what it is. They never reach the
 * identity: it is a cache key shared across users, and it goes to the recall prompt (R43).
 */
const IDENTIFYING_KEY = /serial|tail|registration|vin|hin|plate|address|name|owner/i;

export type NormalizedIdentity = {
  templateId: string;
  specs: Array<{ key: string; value: string }>;
  components: Array<{ slotKey: string; make: string; model: string }>;
};

/** What the Thing is, normalized so two users with the same Thing produce the same key. */
export function normalizeIdentity(context: SuggestionContext): NormalizedIdentity {
  return {
    templateId: context.templateId,
    specs: describing(context.specs),
    components: context.components
      .map((c) => ({ slotKey: c.slotKey, make: norm(c.make), model: norm(c.model) }))
      .filter((c) => c.make || c.model)
      .sort((a, b) => key(a).localeCompare(key(b))),
  };
}

export function identityHash(identity: NormalizedIdentity): string {
  return createHash("sha256").update(JSON.stringify(identity)).digest("hex");
}

function describing(specs: SpecValue[]): Array<{ key: string; value: string }> {
  return specs
    .filter((s) => !IDENTIFYING_KEY.test(s.key) && norm(s.value))
    .map((s) => ({ key: s.key, value: norm(s.value) }))
    .sort((a, b) => a.key.localeCompare(b.key));
}

function norm(s: string): string {
  return s.trim().replace(/\s+/g, " ").toLowerCase();
}

function key(c: { slotKey: string; make: string; model: string }): string {
  return `${c.slotKey}|${c.make}|${c.model}`;
}
