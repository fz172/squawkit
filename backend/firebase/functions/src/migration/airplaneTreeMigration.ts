import { FieldValue } from "firebase-admin/firestore";

import { adminDb } from "../config/firebaseAdmin.js";
import { ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
import { Component } from "../generated/proto/thing/component.js";
import { ThingTemplate } from "../generated/proto/thing/template.js";
import { Thing } from "../generated/proto/thing/thing.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";
import { loadCanonicalTemplates } from "./thingDnaRefresh.js";

/**
 * Restructures airplane component trees to the shape the template now declares (#729).
 *
 * ## What changed, and why the data has to move
 *
 * Phase 1's cutover built `airframe -> engine -> propeller -> (hub, blade)`. The template now
 * declares `engine -> propeller -> blade`:
 *
 *   - **No airframe.** The airframe *is* the thing. Its make, model and serial are the Thing's own,
 *     already in `spec`, so the component only ever repeated the identity block and existed to
 *     parent the engines — which the root can do.
 *   - **No hub.** A propeller's make, model and serial *are* the hub's. Asking for both asked the
 *     same question twice, so the propeller carries them and the blades hang off it directly.
 *
 * A Thing's DNA is authoritative at render (`template_system_design.md` §5), so a Thing left in the
 * old shape walks the new slots and matches nothing: its engines and their serials stay in storage
 * and stop being drawn. **That is why this exists rather than a client-side normaliser** — the data
 * is small, known, and better fixed once at the source than translated on every read forever.
 *
 * ## What it writes
 *
 * Both halves of the same document, in one write:
 *
 *   - `components`, restructured as above.
 *   - `template`, replaced with the canonical airplane preset, because DNA is what the client walks.
 *     Restructuring the components alone would leave a Thing whose tree the app still could not
 *     read — and grafting current slots onto a template still claiming its old version would leave
 *     one whose version says nothing true about its bytes.
 *
 * That second half is now `thingDnaRefresh`'s job as well, and doing it here is not duplication but
 * the same operation reached from the other side: a Thing this restructures must not be left one
 * write short of consistent.
 *
 * ## Idempotent, and safe to re-run
 *
 * A Thing with no `airframe` root and no `hub` is already migrated and is skipped without a write.
 * Re-running after a partial failure repairs only what is left.
 */

export type AirplaneTreeMigrationOptions = {
  /** Restrict to these accounts. Empty or absent migrates every account. */
  onlyUids?: readonly string[];
  /** Report what would change and write nothing. */
  dryRun: boolean;
};

export type MigratedThing = {
  uid: string;
  thingId: string;
  name: string;
  /** Engines lifted out of the airframe wrapper. */
  enginesLifted: number;
  /** Propellers that absorbed a hub's make, model and serial. */
  hubsFolded: number;
};

export type AirplaneTreeMigrationReport = {
  dryRun: boolean;
  scannedUids: number;
  scannedThings: number;
  migrated: MigratedThing[];
  /** Already in the new shape — no airframe root, no hub. Not written. */
  alreadyMigrated: number;
  /** Not an airplane tree: no airframe root and no hub, but components the template did not build. */
  skippedNonAirplane: number;
  /** Payloads that would not decode. Reported, never guessed at. */
  undecodable: Array<{ uid: string; thingId: string }>;
  elapsedMs: number;
};

const SLOT_AIRFRAME = "airframe";
const SLOT_ENGINE = "engine";
const SLOT_PROPELLER = "propeller";
const SLOT_HUB = "hub";
const SLOT_BLADE = "blade";

/**
 * The airplane preset as this repo ships it, read from the compiled asset.
 *
 * Its slots used to be transcribed by hand into this file, because the backend has no copy of the
 * templates — with a test asserting the copy against the same values, which is a test asserting a
 * transcription against itself. `compact_instances` and `spec_fields` landed in #732 and the copy
 * went stale immediately; it took a compile error to notice, and a compile error is the lucky
 * outcome. It now reads the same `.pb` the app bakes in, so there is no second copy to keep.
 */
function canonicalAirplane(): ThingTemplate {
  const airplane = loadCanonicalTemplates().get("airplane");
  if (airplane == null) throw new Error("airplane preset is missing from templates/binary");
  return airplane;
}

/** `"$thingId:${path.join(".")}"` — must match ThingInflater.componentId, a stored id. */
function componentId(thingId: string, path: string[]): string {
  return `${thingId}:${path.join(".")}`;
}

type Restructured = { components: Component[]; enginesLifted: number; hubsFolded: number };

/**
 * The new tree for [thing], or null when there is nothing to do.
 *
 * Null rather than an unchanged copy so the caller can skip the write: re-running this over an
 * already-migrated account should cost reads and nothing else.
 */
export function restructureAirplaneTree(thing: Thing): Restructured | null {
  const roots = thing.components;
  const airframes = roots.filter((c) => c.slotKey === SLOT_AIRFRAME);
  const hasHub = roots.some(hasHubAnywhere);
  if (airframes.length === 0 && !hasHub) return null;

  // Engines come out of the airframe wrapper; anything else already at the root stays where it is.
  const lifted = airframes.flatMap((airframe) =>
    airframe.children.filter((c) => c.slotKey === SLOT_ENGINE),
  );
  const untouched = roots.filter((c) => c.slotKey !== SLOT_AIRFRAME);
  const engines = [...lifted, ...untouched];

  let hubsFolded = 0;
  const rebuilt = engines.map((engine) => {
    const propellers = engine.children.filter((c) => c.slotKey === SLOT_PROPELLER);
    const others = engine.children.filter((c) => c.slotKey !== SLOT_PROPELLER);
    const foldedPropellers = propellers.map((propeller) => {
      const hub = propeller.children.find((c) => c.slotKey === SLOT_HUB);
      const blades = propeller.children.filter((c) => c.slotKey === SLOT_BLADE);
      if (hub != null) hubsFolded++;
      return {
        ...propeller,
        // The hub's identity becomes the propeller's — but never overwrite a value the propeller
        // already carries. A Thing edited on a new build has its own and the hub's is the stale one.
        make: propeller.make || hub?.make || "",
        model: propeller.model || hub?.model || "",
        serial: propeller.serial || hub?.serial || "",
        children: blades,
      };
    });
    return { ...engine, children: [...foldedPropellers, ...others] };
  });

  return {
    components: withDerivedIds(thing.id, rebuilt),
    enginesLifted: lifted.length,
    hubsFolded,
  };
}

function hasHubAnywhere(component: Component): boolean {
  if (component.slotKey === SLOT_HUB) return true;
  return component.children.some(hasHubAnywhere);
}

/**
 * Re-derives every `Component.id` from its new path.
 *
 * The ids encode the path — `thing:airframe.0.engine.1` — so lifting an engine out of the airframe
 * changes them. Nothing joins on a component id today (records still point at components by
 * `ComponentType` + serial), which is what makes this safe to do at all; PRD §4.3's migration to
 * `component_id` has to come *after* this, not before.
 */
function withDerivedIds(thingId: string, components: Component[]): Component[] {
  const walk = (siblings: Component[], parentPath: string[]): Component[] => {
    const seen = new Map<string, number>();
    return siblings.map((component) => {
      const index = seen.get(component.slotKey) ?? 0;
      seen.set(component.slotKey, index + 1);
      const path = [...parentPath, component.slotKey, String(index)];
      return {
        ...component,
        id: componentId(thingId, path),
        children: walk(component.children, path),
      };
    });
  };
  return walk(components, []);
}

async function allUids(): Promise<string[]> {
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

export async function runAirplaneTreeMigration(
  options: AirplaneTreeMigrationOptions,
): Promise<AirplaneTreeMigrationReport> {
  const startedAtMs = Date.now();
  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : await allUids();

  const migrated: MigratedThing[] = [];
  const undecodable: Array<{ uid: string; thingId: string }> = [];
  let scannedThings = 0;
  let alreadyMigrated = 0;
  let skippedNonAirplane = 0;

  for (const uid of uids) {
    const snapshot = await adminDb.collection(`users/${uid}/${ENTITY_SEGMENT_THING}`).get();

    for (const doc of snapshot.docs) {
      scannedThings++;
      const wire = doc.data() as SyncDocWire;
      const bytes = payloadBytes(wire.payload);
      if (bytes == null) {
        undecodable.push({ uid, thingId: doc.id });
        continue;
      }

      let thing: Thing;
      try {
        thing = Thing.decode(bytes);
      } catch {
        // The same refusal the cutover makes: report it rather than assume a shape for bytes we
        // cannot read.
        undecodable.push({ uid, thingId: doc.id });
        continue;
      }

      const restructured = restructureAirplaneTree(thing);
      if (restructured == null) {
        if (thing.components.length === 0) skippedNonAirplane++;
        else alreadyMigrated++;
        continue;
      }

      const updated: Thing = {
        ...thing,
        components: restructured.components,
        // The DNA is what the client walks, so the slots have to move with the components — and
        // the WHOLE template moves, not just the slots. Grafting the current slots onto a template
        // still calling itself v1 would write exactly the lie the version rule exists to prevent:
        // two Things claiming one version while walking different trees, which is what caused the
        // #732 blade regression. A Thing with no DNA still gets none; it renders through the
        // build's own fallback, and freezing a copy into it buys nothing.
        template: thing.template == null ? thing.template : canonicalAirplane(),
      };

      migrated.push({
        uid,
        thingId: doc.id,
        name: thing.name,
        enginesLifted: restructured.enginesLifted,
        hubsFolded: restructured.hubsFolded,
      });

      if (!options.dryRun) {
        await doc.ref.update({
          payload: Buffer.from(Thing.encode(updated).finish()).toString("base64"),
          lastUpdateTimestamp: FieldValue.serverTimestamp(),
        });
      }
    }
  }

  return {
    dryRun: options.dryRun,
    scannedUids: uids.length,
    scannedThings,
    migrated,
    alreadyMigrated,
    skippedNonAirplane,
    undecodable,
    elapsedMs: Date.now() - startedAtMs,
  };
}
