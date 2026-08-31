import { adminDb } from "../config/firebaseAdmin.js";
import { ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
import { Thing } from "../generated/proto/thing/thing.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";

/**
 * Counts Things whose `spec` / `components` were never written — read-only, writes nothing.
 *
 * ## Why an audit before a migration
 *
 * `spec` and `components` were meant to be dual-written from Phase 1. Only the server half shipped:
 * `thingPayloads.ts` populates both during the cutover, and until #717 the client never wrote
 * either. So a Thing created *after* its account was migrated has neither.
 *
 * How many such Things exist is unknown, and it may well be **zero** — an account whose owner has
 * not added a Thing since the cutover has nothing to repair. Writing a migration for a population
 * that might be empty is the expensive way to find out. This answers the question first, and the
 * answer decides whether #718 is a migration or a closed issue.
 *
 * ## Read the number as provisional until the #717 build has shipped
 *
 * The population is still growing. #717 inflates on write, but only on devices running a build that
 * contains it — every older client still writes Things without DNA. A count taken before that build
 * has reached devices is a floor, not a total. Re-run after the release.
 *
 * ## What counts as needing repair
 *
 * `components` is the signal, matching `backfillThing`'s idempotency check. It is the truer one:
 * it is what inflation actually produces, so it cannot report "done" for work that did not happen.
 * `spec` can legitimately be empty — a Thing with no make, model, serial or tail number has nothing
 * to put there — whereas the component tree always has at least an airframe.
 */

export type ThingDnaAuditOptions = {
  /** Restrict to these accounts. Empty or absent audits every account. */
  onlyUids?: readonly string[];
};

export type ThingNeedingDna = {
  uid: string;
  thingId: string;
  /** `name` if it has one, else empty — enough to recognise it without dumping the payload. */
  name: string;
  hasSpec: boolean;
  hasComponents: boolean;
  hasTemplate: boolean;
};

export type ThingDnaAuditReport = {
  scannedUids: number;
  scannedThings: number;
  /** Things with no `components` — the set a backfill would have to repair. */
  needingDna: ThingNeedingDna[];
  /** Things carrying `components` but no `template`: migrated before field 12 existed. */
  missingTemplateOnly: ThingNeedingDna[];
  /** Payloads that would not decode. Reported, never guessed at. */
  undecodable: Array<{ uid: string; thingId: string }>;
  elapsedMs: number;
};

async function allUids(): Promise<string[]> {
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

export async function runThingDnaAudit(
  options: ThingDnaAuditOptions = {},
): Promise<ThingDnaAuditReport> {
  const startedAtMs = Date.now();
  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : await allUids();

  const needingDna: ThingNeedingDna[] = [];
  const missingTemplateOnly: ThingNeedingDna[] = [];
  const undecodable: Array<{ uid: string; thingId: string }> = [];
  let scannedThings = 0;

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
        // Same refusal the cutover makes: report it rather than assume a shape for bytes we
        // cannot read.
        undecodable.push({ uid, thingId: doc.id });
        continue;
      }

      const entry: ThingNeedingDna = {
        uid,
        thingId: doc.id,
        name: thing.name,
        hasSpec: thing.spec.length > 0,
        hasComponents: thing.components.length > 0,
        hasTemplate: thing.template != null,
      };

      if (!entry.hasComponents) {
        needingDna.push(entry);
      } else if (!entry.hasTemplate) {
        // Not a repair the backfill has to make — absent DNA resolves to airplane over a closed set
        // (template_system_design.md §5.3), and #717 fills it on the next write. Counted so the
        // report can distinguish "never inflated" from "inflated before field 12 existed".
        missingTemplateOnly.push(entry);
      }
    }
  }

  return {
    scannedUids: uids.length,
    scannedThings,
    needingDna,
    missingTemplateOnly,
    undecodable,
    elapsedMs: Date.now() - startedAtMs,
  };
}
