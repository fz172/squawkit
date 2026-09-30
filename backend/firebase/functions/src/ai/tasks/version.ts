/**
 * Bump whenever a prompt, schema, provider choice or validator changes output (design §6.6). It is
 * part of every cache key, so a bump invalidates the cache, and it is stamped on every result and
 * `TaskOrigin` so a bad batch of tasks can be found later.
 */
export const GENERATION_VERSION = "tasks-2";
