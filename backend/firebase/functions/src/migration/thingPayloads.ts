import { Attachment } from "../generated/proto/thing/attachment.js";
import { MaintenanceLog } from "../generated/proto/thing/maintenance_log.js";
import { MaintenanceTask } from "../generated/proto/thing/maintenance_task.js";
import { Squawk } from "../generated/proto/thing/squawk.js";
import { Component } from "../generated/proto/thing/component.js";
import { Spec } from "../generated/proto/thing/spec.js";
import { Thing } from "../generated/proto/thing/thing.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";

/**
 * The two payload rewrites the Aircraft → Thing cutover performs, as pure functions.
 *
 * See docs/product/thing_migration_design.md §4.2. These were originally scoped as on-device work
 * (`LocalThingPathMigrator`, task A6) and moved here when that class was withdrawn: running them
 * server-side means each executes exactly **once per document against the authoritative copy**,
 * rather than N times across N devices holding partial copies. That is what makes the determinism
 * requirement below true by construction instead of a property N independent devices have to agree
 * on.
 *
 * Kept separate from the copy driver (`thingCutover.ts`) so they can be tested as plain input →
 * output, with no emulator in the loop.
 *
 * **Everything here goes through the generated proto API, never raw bytes.** The path segment being
 * replaced is length-changing (`aircraft` → `thing`), so a string replace over the encoded form
 * would corrupt every length-delimited field after it. Encoded payloads are also **base64 strings**,
 * not byte arrays (#428) — decoding them as bytes silently yields an empty record, which is exactly
 * how a sweep once deleted real photos.
 */

/** Schema strings whose payloads carry `Attachment`s and therefore embed a storage path. */
const ATTACHMENT_CARRYING = {
  MAINTENANCE_LOG: "aircraft.MaintenanceLog",
  MAINTENANCE_TASK: "aircraft.MaintenanceTask",
  SQUAWK: "aircraft.Squawk",
} as const;

/** The schema string a Thing document carries after the rename. */
export const THING_SCHEMA = "thing.Thing";
/** The schema string it carried before. */
export const LEGACY_THING_SCHEMA = "aircraft.Aircraft";

export type TransformResult<T> = {
  value: T;
  /** How many fields this transform actually changed — 0 means the document was already migrated. */
  changed: number;
};

/** Base64, the shape `FirestoreSyncWriter` writes and every client reads (#428). */
function encodePayload(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64");
}

/**
 * Rewrite the `/aircraft/` segment of an attachment's storage path.
 *
 * The path is denormalized INSIDE the payload (design §2.6), so moving the Storage objects does not
 * fix the pointers — this does. Operating on the decoded `string` field makes a plain replace safe
 * here, which it would not be on the encoded form.
 *
 * Ids are opaque generated strings that cannot contain `/`, so `/aircraft/` occurs at most once per
 * path, at a fixed position.
 */
function rewriteStoragePath(path: string): string {
  return path.replace("/aircraft/", "/thing/");
}

function rewriteAttachments(attachments: Attachment[]): number {
  let changed = 0;
  for (const attachment of attachments) {
    if (attachment.storagePath == null || attachment.storagePath.length === 0) continue;
    const next = rewriteStoragePath(attachment.storagePath);
    if (next !== attachment.storagePath) {
      attachment.storagePath = next;
      changed++;
    }
  }
  return changed;
}

/**
 * Rewrite every embedded `Attachment.storage_path` on a record payload.
 *
 * Returns the payload unchanged (and `changed: 0`) for a schema that owns no attachments, for an
 * absent payload, and for one that will not decode. **Refusing to guess is the point**: a payload
 * this cannot read is indistinguishable from one whose paths are already correct, and rewriting
 * bytes we do not understand is how attachments get lost.
 */
export function rewriteRecordStoragePaths(doc: SyncDocWire): TransformResult<SyncDocWire> {
  const schema = doc.schema ?? "";
  const bytes = payloadBytes(doc.payload);
  if (bytes == null) return { value: doc, changed: 0 };

  try {
    switch (schema) {
      case ATTACHMENT_CARRYING.MAINTENANCE_LOG: {
        const decoded = MaintenanceLog.decode(bytes);
        const changed = rewriteAttachments(decoded.attachments);
        if (changed === 0) return { value: doc, changed: 0 };
        return {
          value: { ...doc, payload: encodePayload(MaintenanceLog.encode(decoded).finish()) },
          changed,
        };
      }
      case ATTACHMENT_CARRYING.MAINTENANCE_TASK: {
        const decoded = MaintenanceTask.decode(bytes);
        const changed = rewriteAttachments(decoded.attachments);
        if (changed === 0) return { value: doc, changed: 0 };
        return {
          value: { ...doc, payload: encodePayload(MaintenanceTask.encode(decoded).finish()) },
          changed,
        };
      }
      case ATTACHMENT_CARRYING.SQUAWK: {
        const decoded = Squawk.decode(bytes);
        const changed = rewriteAttachments(decoded.attachments);
        if (changed === 0) return { value: doc, changed: 0 };
        return {
          value: { ...doc, payload: encodePayload(Squawk.encode(decoded).finish()) },
          changed,
        };
      }
      default:
        return { value: doc, changed: 0 };
    }
  } catch {
    // Undecodable. Copy it through byte-for-byte rather than dropping or guessing at it — the
    // cutover's job is to move data, and a payload we cannot read still belongs to the user.
    return { value: doc, changed: 0 };
  }
}

/**
 * A component id, derived from `(thingId, slotKey, index)` and nothing else.
 *
 * **Determinism is load-bearing** (PRD §9.1). Random ids would migrate the same aircraft to
 * different component ids on different runs, and last-writer-wins would then silently reassign every
 * log's component. Running server-side makes this a single computation rather than one that N
 * devices must independently agree on — but the derivation stays deterministic anyway, so a re-run
 * of the script (which is expected: §5.1 retries until zero failures) produces identical output.
 *
 * The path is included so that a `blade` under engine 0 and a `blade` under engine 1 do not collide.
 */
export function componentId(thingId: string, path: readonly string[]): string {
  return `${thingId}:${path.join(".")}`;
}

function specOf(entries: ReadonlyArray<readonly [string, string]>): Spec[] {
  return entries
    .filter(([, value]) => value.length > 0)
    .map(([key, value]) => Spec.fromPartial({ key, value }));
}

/**
 * The airplane component tree, exactly as PRD §9.1 specifies: one `airframe` carrying the Thing's
 * own make/model/serial, one `engine` per `Engine`, a `propeller` child per engine, and `hub` /
 * `blade` children under that.
 *
 * Empty legacy fields still produce their component. The tree's SHAPE is derived from the aircraft's
 * structure (how many engines, how many blades), not from which text fields the user happened to
 * fill in — so a half-filled aircraft still gets a stable skeleton to hang logs off, and filling a
 * field in later does not renumber anything.
 */
function buildComponents(thing: Thing): Component[] {
  const airframe = Component.fromPartial({
    id: componentId(thing.id, ["airframe", "0"]),
    slotKey: "airframe",
    label: "Airframe",
    make: thing.make,
    model: thing.model,
    serial: thing.serial,
    children: thing.engine.map((engine, engineIndex) => {
      const enginePath = ["engine", String(engineIndex)];
      const propeller = engine.propeller;
      const propellerChildren: Component[] = [];

      if (propeller != null) {
        const propellerPath = [...enginePath, "propeller", "0"];
        const hub = propeller.hub;
        if (hub != null) {
          propellerChildren.push(
            Component.fromPartial({
              id: componentId(thing.id, [...propellerPath, "hub", "0"]),
              slotKey: "hub",
              label: "Hub",
              make: hub.make,
              model: hub.model,
              serial: hub.serial,
            }),
          );
        }
        propeller.blades.forEach((blade, bladeIndex) => {
          propellerChildren.push(
            Component.fromPartial({
              id: componentId(thing.id, [...propellerPath, "blade", String(bladeIndex)]),
              slotKey: "blade",
              label: `Blade ${bladeIndex + 1}`,
              make: blade.make,
              model: blade.model,
              serial: blade.serial,
            }),
          );
        });
      }

      return Component.fromPartial({
        id: componentId(thing.id, enginePath),
        slotKey: "engine",
        label: thing.engine.length > 1 ? `Engine ${engineIndex + 1}` : "Engine",
        make: engine.make,
        model: engine.model,
        serial: engine.serial,
        children:
          propeller == null
            ? []
            : [
                Component.fromPartial({
                  id: componentId(thing.id, [...enginePath, "propeller", "0"]),
                  slotKey: "propeller",
                  label: "Propeller",
                  children: propellerChildren,
                }),
              ],
      });
    }),
  });

  return [airframe];
}

/** `tail_number` if it has one, else `"$make $model"`, else empty (PRD §9.1). */
function nameOf(thing: Thing): string {
  if (thing.tailNumber.length > 0) return thing.tailNumber;
  return [thing.make, thing.model].filter((part) => part.length > 0).join(" ");
}

/**
 * Backfill `template_id` / `template_version` / `name` / `spec` / `components` on a Thing payload.
 *
 * The legacy fields 2–6 are left populated and untouched — they are transitional, not replaced, and
 * a pre-migration client still reads them (design §3.1). Nothing is renumbered or removed, so a
 * client built against the old schema round-trips the new fields as unknowns
 * (`ThingUnknownFieldRetentionTest`).
 *
 * **Idempotent**: a payload that already carries a `template_id` is returned untouched. The script
 * is expected to be re-run until it reports zero failures (§5.1), so a second pass over an
 * already-migrated document must not rebuild the tree — and because the ids are derived rather than
 * generated, rebuilding it would produce the same bytes anyway.
 */
export function backfillThing(doc: SyncDocWire): TransformResult<SyncDocWire> {
  const bytes = payloadBytes(doc.payload);
  if (bytes == null) return { value: doc, changed: 0 };

  let thing: Thing;
  try {
    thing = Thing.decode(bytes);
  } catch {
    // Same refusal as above: copy it through rather than replacing an unreadable payload with a
    // synthesized one, which would destroy whatever it actually held.
    return { value: doc, changed: 0 };
  }

  // Idempotency signal. A Thing without DNA is legacy, and legacy is always airplane, so nothing needs to be
  // stored to say which template applies. `components` is a truer signal anyway: it is what this
  // function actually produces, so it cannot report "done" for work that did not happen.
  const alreadyBackfilled = thing.components.length > 0;
  const schemaNeedsUpdate = doc.schema !== THING_SCHEMA;

  if (alreadyBackfilled) {
    // The payload is done; the envelope may not be, if a previous run failed between the two.
    return schemaNeedsUpdate
      ? { value: { ...doc, schema: THING_SCHEMA }, changed: 1 }
      : { value: doc, changed: 0 };
  }

  thing.name = nameOf(thing);
  thing.spec = specOf([
    ["make", thing.make],
    ["model", thing.model],
    ["serial", thing.serial],
    ["tail_number", thing.tailNumber],
  ]);
  thing.components = buildComponents(thing);

  return {
    value: {
      ...doc,
      schema: THING_SCHEMA,
      payload: encodePayload(Thing.encode(thing).finish()),
    },
    changed: 1,
  };
}
