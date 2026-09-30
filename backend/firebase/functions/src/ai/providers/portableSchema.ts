import type { JsonSchema } from "./types.js";

/**
 * The JSON Schema subset every provider accepts as structured output: every object closed, every
 * property required. An optional value is written as a `null` union (`type: ["string", "null"]`)
 * instead of being left out of `required`. Strict enough that adding a vendor needs no schema work.
 */
const ALLOWED_KEYWORDS = new Set([
  "type",
  "properties",
  "required",
  "additionalProperties",
  "items",
  "enum",
  "anyOf",
  "description",
]);

/** Throws naming the first path that breaks the subset. Pipeline schemas call it in their tests. */
export function assertPortableSchema(schema: JsonSchema, path = "$"): void {
  for (const key of Object.keys(schema)) {
    if (!ALLOWED_KEYWORDS.has(key)) throw new Error(`${path}: keyword "${key}" is not portable`);
  }

  const properties = schema.properties as Record<string, JsonSchema> | undefined;
  if (properties !== undefined || typeIncludes(schema, "object")) {
    if (schema.additionalProperties !== false) {
      throw new Error(`${path}: objects need additionalProperties: false`);
    }
    const keys = Object.keys(properties ?? {});
    const required = new Set((schema.required as string[] | undefined) ?? []);
    const missing = keys.filter((k) => !required.has(k));
    if (missing.length > 0) {
      throw new Error(`${path}: every property must be required (missing ${missing.join(", ")})`);
    }
    for (const k of keys) assertPortableSchema(properties![k], `${path}.${k}`);
  }

  if (schema.items !== undefined) assertPortableSchema(schema.items as JsonSchema, `${path}[]`);
  const anyOf = schema.anyOf as JsonSchema[] | undefined;
  anyOf?.forEach((branch, i) => assertPortableSchema(branch, `${path}|${i}`));
}

function typeIncludes(schema: JsonSchema, type: string): boolean {
  const t = schema.type;
  return t === type || (Array.isArray(t) && t.includes(type));
}
