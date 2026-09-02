#!/usr/bin/env node
// @ts-check
/**
 * READ-ONLY probe: what shape is every stored Thing actually in?
 *
 * The airplane-tree dry run reports "already migrated" for anything with no `airframe` root and no
 * `hub` — which is equally true of a car, a boat and a home. It also decides purely on
 * `components` and never looks at `template.component_slots`, so a Thing whose DNA still declares
 * the old slots is invisible to it. This answers both.
 *
 *   npm run shape-probe
 *   npm run shape-probe -- --out probe.json
 *
 * ## What it found, 2026-09-01
 *
 * 40 Things across 25 accounts: 11 airplane, 4 automotive, 3 home, 2 boat, 1 bike, and 19 with no
 * `template` at all. **Zero stale DNA and zero stale components, anywhere** — so #729's tree
 * migration has nothing to do, which its own dry run reported but could not distinguish from
 * "these were never airplanes".
 *
 * The 19 without DNA are the state #718 closed on: absent DNA resolves to airplane over a closed
 * set, and #717 fills it on each Thing's next write. That audit counted 21; this one counts 19,
 * which is the mechanism draining the set rather than a discrepancy.
 */

import { writeFileSync } from "node:fs";

import { adminDb } from "../lib/config/firebaseAdmin.js";
import { ENTITY_SEGMENT_THING } from "../lib/config/entitySegment.js";
import { Thing } from "../lib/generated/proto/thing/thing.js";
import { payloadBytes } from "../lib/shared/syncDocWire.js";

const OLD_SLOTS = new Set(["airframe", "hub"]);

/** @param {any[]} slots @returns {string[]} */
function slotKeys(slots) {
  const out = [];
  const walk = (list) => {
    for (const s of list ?? []) {
      out.push(s.slotKey);
      walk(s.children);
    }
  };
  walk(slots);
  return out;
}

/** @param {any[]} components @returns {string[]} */
function componentKeys(components) {
  const out = [];
  const walk = (list) => {
    for (const c of list ?? []) {
      out.push(c.slotKey);
      walk(c.children);
    }
  };
  walk(components);
  return out;
}

async function allUids() {
  // listDocuments, not get: a `users/{uid}` doc carries no fields of its own, only subcollections,
  // and a fieldless document is "missing" to a query. This is what the migration does.
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

async function main() {
  const outIndex = process.argv.indexOf("--out");
  const outPath = outIndex >= 0 ? process.argv[outIndex + 1] : null;

  const rows = [];
  for (const uid of await allUids()) {
    const snap = await adminDb.collection(`users/${uid}/${ENTITY_SEGMENT_THING}`).get();
    for (const doc of snap.docs) {
      const bytes = payloadBytes(/** @type {any} */ (doc.data()).payload);
      if (bytes == null) {
        rows.push({ uid, thingId: doc.id, templateId: "(undecodable)" });
        continue;
      }
      let thing;
      try {
        thing = Thing.decode(bytes);
      } catch {
        rows.push({ uid, thingId: doc.id, templateId: "(undecodable)" });
        continue;
      }
      const dnaSlots = slotKeys(thing.template?.componentSlots);
      const compSlots = componentKeys(thing.components);
      rows.push({
        uid,
        thingId: doc.id,
        name: thing.name,
        templateId: thing.template?.id ?? "(no template)",
        templateVersion: thing.template?.version ?? 0,
        dnaSlots,
        componentSlots: compSlots,
        staleDna: dnaSlots.some((k) => OLD_SLOTS.has(k)),
        staleComponents: compSlots.some((k) => OLD_SLOTS.has(k)),
        componentCount: compSlots.length,
      });
    }
  }

  /** @param {(r: any) => boolean} p */
  const count = (p) => rows.filter(p).length;
  const byTemplate = new Map();
  for (const r of rows) byTemplate.set(r.templateId, (byTemplate.get(r.templateId) ?? 0) + 1);

  console.log(`Scanned ${rows.length} things\n`);
  console.log("By template:");
  for (const [id, n] of [...byTemplate].sort((a, b) => b[1] - a[1])) {
    console.log(`  ${id.padEnd(20)} ${n}`);
  }

  const planes = rows.filter((r) => r.templateId === "airplane");
  console.log(`\nAirplanes: ${planes.length}`);
  console.log(`  stale DNA (declares airframe/hub):  ${count((r) => r.templateId === "airplane" && r.staleDna)}`);
  console.log(`  stale components:                   ${count((r) => r.templateId === "airplane" && r.staleComponents)}`);
  console.log(`  no components at all:               ${count((r) => r.templateId === "airplane" && r.componentCount === 0)}`);
  console.log(`  no template DNA at all:             ${count((r) => r.templateId === "airplane" && r.dnaSlots.length === 0)}`);

  console.log(`\nAll things with stale DNA anywhere:  ${count((r) => r.staleDna)}`);
  console.log(`All things with no template:         ${count((r) => r.templateId === "(no template)")}`);

  const problems = rows.filter((r) => r.staleDna || r.staleComponents);
  if (problems.length > 0) {
    console.log("\nThings still carrying old slots:");
    for (const r of problems) {
      console.log(
        `  ${r.uid}/${r.thingId} ${r.name || "(unnamed)"} [${r.templateId}] ` +
          `dna=[${r.dnaSlots.join(",")}] components=[${r.componentSlots.join(",")}]`,
      );
    }
  }

  if (outPath != null) {
    writeFileSync(outPath, JSON.stringify(rows, null, 2));
    console.log(`\nFull report written to ${outPath}`);
  }
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
