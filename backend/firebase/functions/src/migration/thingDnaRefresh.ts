import { readFileSync, readdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

import { FieldValue } from "firebase-admin/firestore";

import { adminDb } from "../config/firebaseAdmin.js";
import { ENTITY_SEGMENT_THING } from "../config/entitySegment.js";
import { Thing } from "../generated/proto/thing/thing.js";
import { ThingTemplate } from "../generated/proto/thing/template.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";

/**
 * Replaces each Thing's frozen DNA with the canonical preset this repo now ships (#732).
 *
 * ## Why a Thing goes stale in the first place
 *
 * A Thing carries its whole template inline — `template_system_design.md` §5's DNA model — and
 * `BakedInTemplateRegistry.forThingWithFallback` returns those stored bytes verbatim, deliberately
 * never refreshing them from the pool by id. That is the right default: at the point a preset can
 * be forked into a custom template, consulting the pool would silently revert every customisation.
 *
 * The cost the registry's own comment names is this script's reason to exist: **a preset edit
 * reaches only Things created after it.** #732 added `compact_instances` to the blade slot, so a
 * new aeroplane draws its blades as a compact grid of chips and an aeroplane created last month
 * draws the same blades as a flat list of full-width blocks — same build, same screen, different
 * frozen bytes. Nothing is wrong with the code. The data is old.
 *
 * ## What it writes
 *
 * `thing.template`, wholesale, from the compiled `.pb` beside the text proto that authored it. Not
 * a field-by-field patch: a patch has to be written again for the next preset edit, and every one
 * of them is the same operation. `components` are untouched — this is a template refresh, which is
 * exactly what `airplaneTreeMigration` said it was **not** doing, and the two are complementary.
 * Run the tree migration first if a Thing still carries the old `airframe`/`hub` shape; the slots
 * it writes are a hand transcription that this then supersedes with the compiled bytes.
 *
 * ## Safe while nothing forks a preset
 *
 * A wholesale replace is only correct because a stored template can currently only be a copy of a
 * canonical one — the fetched pool and custom templates are designed but unbuilt (#726, #727). A
 * Thing whose `template.id` names no canonical preset is therefore already the unexpected case, and
 * is **reported and skipped** rather than guessed at. When custom templates land, that skip is the
 * hook this needs: refresh what still matches a preset, leave a fork alone.
 *
 * ## Idempotent, and safe to re-run
 *
 * A Thing whose DNA already re-encodes to the canonical bytes is skipped without a write, so a
 * second run costs reads and nothing else.
 */

export type ThingDnaRefreshOptions = {
  /** Restrict to these accounts. Empty or absent refreshes every account. */
  onlyUids?: readonly string[];
  /** Restrict to Things on these template ids — `["airplane"]` for the aeroplanes alone. */
  onlyTemplateIds?: readonly string[];
  /** Report what would change and write nothing. */
  dryRun: boolean;
  /**
   * Also freeze DNA onto Things that carry none. **Off by default, deliberately.**
   *
   * A Thing with no DNA resolves through the build's own fallback, so it is never stale — writing
   * a copy of the current preset into it trades that for a frozen snapshot every future bump has to
   * migrate. `airplaneTreeMigration` refused for the same reason. The flag exists because the
   * opposite argument is also real: a Thing with no version cannot be identified from its payload
   * at all, which is the problem versioning was introduced to solve. Choose per run, knowing which
   * trade is being made.
   */
  inflateMissing?: boolean;
  /** Where the compiled presets live. Defaults to `core/template/templates/binary` in this repo. */
  templatesDir?: string;
};

export type RefreshedThing = {
  uid: string;
  thingId: string;
  name: string;
  templateId: string;
  /** The version the Thing carried. Null when it carried no DNA at all. */
  fromVersion: number | null;
  toVersion: number;
};

export type ThingDnaRefreshReport = {
  dryRun: boolean;
  /** `id@version` of every preset this run compared against — the pool, as loaded. */
  canonical: string[];
  scannedUids: number;
  scannedThings: number;
  /** DNA replaced with the canonical bytes. */
  refreshed: RefreshedThing[];
  /** Had no DNA at all, and was inflated to the airplane preset the fallback already resolves to. */
  inflated: RefreshedThing[];
  /** Had no DNA and was left that way — `inflateMissing` was off. Still renders via the fallback. */
  leftWithoutDna: number;
  /** DNA already re-encodes to the canonical bytes. Not written. */
  alreadyCurrent: number;
  /** `template.id` names no shipped preset — a fork, or an id from a newer build. Left alone. */
  unknownTemplateId: Array<{ uid: string; thingId: string; templateId: string }>;
  /** Excluded by `onlyTemplateIds`. */
  filteredOut: number;
  /** Payloads that would not decode. Reported, never guessed at. */
  undecodable: Array<{ uid: string; thingId: string }>;
  elapsedMs: number;
};

/**
 * The preset a Thing with no DNA at all resolves to.
 *
 * Not a guess: `thing.proto` reserves fields 7 and 8 (`template_id`, `template_version`) precisely
 * because a Thing without DNA can only predate templates, and every one of those is an aeroplane —
 * a closed set, since any client able to create something else necessarily has DNA support. The
 * client's `forThingWithFallback` already falls back here, so inflating changes what is stored
 * without changing what is drawn.
 */
const FALLBACK_TEMPLATE_ID = "airplane";

/**
 * `core/template/templates/binary`, found by walking up from the working directory.
 *
 * Read from disk rather than transcribed into TypeScript. `airplaneTreeMigration` transcribed its
 * slots by hand — conceding in a comment that a test had to guard the copy — and that copy went
 * stale within three preset edits, to a compile error. One source of bytes cannot drift from itself.
 *
 * From the working directory rather than from this module because `tsconfig` emits CommonJS, where
 * `import.meta.url` is a compile error. Both entry points — `npm run dna-refresh` and vitest — run
 * with the functions package as cwd, so the walk is three hops in either.
 */
export function defaultTemplatesDir(): string {
  let dir = resolve(process.cwd());
  for (let hops = 0; hops < 12; hops++) {
    const candidate = join(dir, "core", "template", "templates", "binary");
    try {
      if (readdirSync(candidate).length > 0) return candidate;
    } catch {
      // Not this level. Keep walking.
    }
    const parent = dirname(dir);
    if (parent === dir) break;
    dir = parent;
  }
  throw new Error(
    `could not find core/template/templates/binary above ${process.cwd()} — ` +
      "run this from the repo, or pass templatesDir",
  );
}

/**
 * Every compiled preset, keyed by its own declared id, highest version winning.
 *
 * Keyed by what the bytes say rather than by the filename, so a mis-named `.pb` cannot quietly
 * become the canonical answer for the wrong preset.
 */
export function loadCanonicalTemplates(
  dir: string = defaultTemplatesDir(),
): Map<string, ThingTemplate> {
  const byId = new Map<string, ThingTemplate>();
  for (const file of readdirSync(dir).sort()) {
    if (!file.endsWith(".pb")) continue;
    const template = ThingTemplate.decode(new Uint8Array(readFileSync(join(dir, file))));
    const existing = byId.get(template.id);
    if (existing == null || template.version > existing.version) byId.set(template.id, template);
  }
  return byId;
}

/** Canonical proto bytes for a template, re-encoded so two messages compare by value, not by origin. */
function normalisedBytes(template: ThingTemplate): string {
  return Buffer.from(ThingTemplate.encode(template).finish()).toString("base64");
}

async function allUids(): Promise<string[]> {
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

export async function runThingDnaRefresh(
  options: ThingDnaRefreshOptions,
): Promise<ThingDnaRefreshReport> {
  const startedAtMs = Date.now();
  const canonical = loadCanonicalTemplates(options.templatesDir);
  const canonicalBytes = new Map(
    [...canonical].map(([id, template]) => [id, normalisedBytes(template)]),
  );
  const wanted =
    options.onlyTemplateIds != null && options.onlyTemplateIds.length > 0
      ? new Set(options.onlyTemplateIds)
      : null;
  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : await allUids();

  const refreshed: RefreshedThing[] = [];
  const inflated: RefreshedThing[] = [];
  const unknownTemplateId: Array<{ uid: string; thingId: string; templateId: string }> = [];
  const undecodable: Array<{ uid: string; thingId: string }> = [];
  let scannedThings = 0;
  let alreadyCurrent = 0;
  let filteredOut = 0;
  let leftWithoutDna = 0;

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
        // The same refusal the cutover and the tree migration make: report bytes we cannot read
        // rather than assume a shape for them.
        undecodable.push({ uid, thingId: doc.id });
        continue;
      }

      const stored = thing.template;
      if (stored == null && options.inflateMissing !== true) {
        leftWithoutDna++;
        continue;
      }
      const templateId = stored?.id ?? FALLBACK_TEMPLATE_ID;
      if (wanted != null && !wanted.has(templateId)) {
        filteredOut++;
        continue;
      }

      const target = canonical.get(templateId);
      if (target == null) {
        unknownTemplateId.push({ uid, thingId: doc.id, templateId });
        continue;
      }

      if (stored != null && normalisedBytes(stored) === canonicalBytes.get(templateId)) {
        alreadyCurrent++;
        continue;
      }

      const entry: RefreshedThing = {
        uid,
        thingId: doc.id,
        name: thing.name,
        templateId,
        fromVersion: stored?.version ?? null,
        toVersion: target.version,
      };
      (stored == null ? inflated : refreshed).push(entry);

      if (!options.dryRun) {
        const updated: Thing = { ...thing, template: target };
        await doc.ref.update({
          payload: Buffer.from(Thing.encode(updated).finish()).toString("base64"),
          lastUpdateTimestamp: FieldValue.serverTimestamp(),
        });
      }
    }
  }

  return {
    dryRun: options.dryRun,
    canonical: [...canonical.values()].map((t) => `${t.id}@${t.version}`).sort(),
    scannedUids: uids.length,
    scannedThings,
    refreshed,
    inflated,
    leftWithoutDna,
    alreadyCurrent,
    unknownTemplateId,
    filteredOut,
    undecodable,
    elapsedMs: Date.now() - startedAtMs,
  };
}
