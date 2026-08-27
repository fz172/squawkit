/**
 * The entity-tree path segment, during the Aircraft → Thing migration window.
 *
 * See docs/product/thing_migration_design.md §2.7 (and task B9). Every per-aircraft document lives
 * under `users/{uid}/<segment>/{acId}/...`, and that segment is moving from `aircraft` to `thing`.
 *
 * Cloud Functions v2 Firestore triggers take a **literal, deploy-time** document path — there is no
 * "either segment" wildcard — and a function deploy is **global**, so a single registration cannot
 * serve migrated and un-migrated accounts at once. The only way to cover both is to register each
 * trigger twice, once per segment, and route both registrations into the same handler.
 *
 * The segment the event fired for is then load-bearing *inside* the handler too: an `aircraft`-path
 * write must cascade to `aircraft`-path children and blobs, not `thing`-path ones. So handlers take
 * the segment as a parameter rather than hardcoding either value.
 *
 * Phase F3 deletes every `LEGACY` registration once no account is left on the old paths, after which
 * this module collapses to a single constant.
 */

export const ENTITY_SEGMENT_LEGACY = "aircraft" as const;
export const ENTITY_SEGMENT_THING = "thing" as const;

export type EntitySegment = typeof ENTITY_SEGMENT_LEGACY | typeof ENTITY_SEGMENT_THING;

/** Both segments a trigger must be registered on for the duration of the migration window. */
export const ENTITY_SEGMENTS: readonly EntitySegment[] = [
  ENTITY_SEGMENT_LEGACY,
  ENTITY_SEGMENT_THING,
];

/** The aircraft/thing document itself. */
export function entityDocPath(uid: string, acId: string, segment: EntitySegment): string {
  return `users/${uid}/${segment}/${acId}`;
}

/** The blob prefix for one aircraft/thing. Trailing slash included — it is used as a prefix. */
export function entityBlobPrefix(uid: string, acId: string, segment: EntitySegment): string {
  return `users/${uid}/${segment}/${acId}/blobs/`;
}

/** One blob object under an aircraft/thing. */
export function entityBlobPath(
  uid: string,
  acId: string,
  blobId: string,
  segment: EntitySegment,
): string {
  return `${entityBlobPrefix(uid, acId, segment)}${blobId}`;
}
