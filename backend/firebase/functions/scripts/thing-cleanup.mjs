#!/usr/bin/env node
// @ts-check
/**
 * Aircraft → Thing cleanup — the Phase F deletion pass (thing_migration_design.md §7, task B4).
 *
 * Deletes the OLD `/users/{uid}/aircraft/...` Firestore subtree and the old
 * `users/{uid}/aircraft/{acId}/blobs/**` Storage objects, once the copy at `/thing/` has been
 * re-proven complete. The counterpart to `thing-cutover.mjs`, kept as a separate program because
 * the cutover's whole safety property is that it never deletes — folding this in would spend that.
 *
 * WHAT AUTHORISES A DELETE IS RE-VERIFICATION, NOT ELAPSED TIME. Before removing anything, each
 * aircraft is re-checked from scratch against the live data: every source document must be present
 * at the destination *by id* (not merely by count — equal counts with different ids would pass a
 * count check and still have lost data), and every source blob must have a byte-identical
 * counterpart. Anything that fails is skipped and reported, never deleted.
 *
 * The 7-day grace window is a separate, human thing: it is about devices picking up the new build
 * and about having time to notice a problem while the old paths are still there to fall back to.
 * There is no stored per-account copy timestamp to check it against, so it is asserted with
 * `--grace-elapsed` rather than derived from a date the script would only be guessing at.
 *
 *   npm run cleanup -- --dry-run                        # report what would go; delete nothing
 *   npm run cleanup -- --grace-elapsed --yes            # the real thing
 *   npm run cleanup -- --only uid1,uid2 --grace-elapsed --yes
 *   npm run cleanup -- --dry-run --out cleanup.json     # full report, including every skip reason
 *
 * Credentials & project — identical to thing-cutover.mjs; see its header, including the
 * `$(command -v node)` requirement when running under `firebase emulators:exec`.
 *
 * ORDERING (§7): run only after the 7-day window has elapsed since D3's verified copy, and only
 * once the Phase 1 client build has reached every device (E2). Until then the old paths are the
 * rollback, and this is the program that removes it.
 */

import { writeFileSync } from "node:fs";
import { createInterface } from "node:readline/promises";

import { runThingCleanup } from "../lib/migration/thingCleanup.js";

function parseArgs(argv) {
  const args = {
    dryRun: false, yes: false, graceElapsed: false, skipChecksum: false,
    onlyUids: [], out: null, bucket: null,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--yes") args.yes = true;
    else if (arg === "--grace-elapsed") args.graceElapsed = true;
    else if (arg === "--skip-checksum") args.skipChecksum = true;
    else if (arg === "--only") args.onlyUids = (argv[++i] ?? "").split(",").filter(Boolean);
    else if (arg === "--out") args.out = argv[++i] ?? null;
    else if (arg === "--bucket") args.bucket = argv[++i] ?? null;
    else {
      console.error(`Unknown argument: ${arg}`);
      process.exit(2);
    }
  }
  return args;
}

/** Same resolution and same reasoning as thing-cutover.mjs — see that file's note. */
function resolvedBucket(explicit) {
  if (explicit != null && explicit.length > 0) return explicit;
  if (process.env.FIREBASE_STORAGE_BUCKET != null) return process.env.FIREBASE_STORAGE_BUCKET;
  try {
    const config = JSON.parse(process.env.FIREBASE_CONFIG ?? "{}");
    if (typeof config.storageBucket === "string" && config.storageBucket.length > 0) {
      return config.storageBucket;
    }
  } catch {
    // absent or malformed — fall through
  }
  const project = process.env.GOOGLE_CLOUD_PROJECT ?? process.env.GCLOUD_PROJECT;
  return project != null ? `${project}.firebasestorage.app` : null;
}

function resolvedProject() {
  return (
    process.env.GOOGLE_CLOUD_PROJECT ??
    process.env.GCLOUD_PROJECT ??
    process.env.FIREBASE_PROJECT ??
    "(unset — Admin SDK will infer from GOOGLE_APPLICATION_CREDENTIALS)"
  );
}

function formatBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const emulator = process.env.FIRESTORE_EMULATOR_HOST != null;
  const bucket = resolvedBucket(args.bucket);

  console.log("");
  console.log("  Aircraft → Thing cleanup  (DELETES the old paths)");
  console.log(`  project:   ${resolvedProject()}${emulator ? "  [EMULATOR]" : ""}`);
  console.log(`  mode:      ${args.dryRun ? "DRY RUN (deletes nothing)" : "LIVE (DELETES DATA)"}`);
  console.log(`  scope:     ${args.onlyUids.length > 0 ? args.onlyUids.join(", ") : "every account"}`);
  console.log(`  bucket:    ${bucket ?? "(unresolved — pass --bucket)"}`);
  console.log(`  blob check: ${args.skipChecksum ? "size only  [NOT RECOMMENDED]" : "size + sha256"}`);
  console.log("");

  if (!args.dryRun && !args.yes) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    console.log("  This permanently deletes the old Firestore subtree and Storage objects.");
    console.log("  After this there is no rollback to the /aircraft/ paths.");
    const answer = await rl.question("  Type DELETE to proceed: ");
    rl.close();
    if (answer.trim() !== "DELETE") {
      console.log("  Aborted.");
      process.exit(1);
    }
  }

  const report = await runThingCleanup({
    dryRun: args.dryRun,
    onlyUids: args.onlyUids,
    graceElapsed: args.graceElapsed,
    skipChecksum: args.skipChecksum,
    bucketName: bucket ?? undefined,
  });

  const t = report.totals;
  console.log("");
  console.log(`  accounts:   ${report.succeeded.length} ok, ${report.failed.length} failed`);
  console.log(`  aircraft:   ${t.aircraftDeleted} cleaned, ${t.aircraftSkipped} skipped`);
  console.log(`  documents:  ${t.documentsDeleted}`);
  console.log(`  blobs:      ${t.blobsDeleted} (${formatBytes(t.bytesReclaimed)} reclaimed)`);

  if (report.skipped.length > 0) {
    // Skips are the interesting output, not an afterthought: each one is an aircraft whose copy
    // could not be re-proven, which is a thing to go and look at rather than to run past.
    console.log("");
    console.log("  SKIPPED (left in place — copy could not be re-verified):");
    for (const s of report.skipped) console.log(`    ${s.uid}/${s.acId}  ${s.reason}`);
  }

  if (report.failed.length > 0) {
    console.log("");
    console.log("  FAILED:");
    for (const { uid, error } of report.failed) console.log(`    ${uid}  ${error}`);
  }

  if (args.out != null) {
    writeFileSync(args.out, JSON.stringify(report, null, 2));
    console.log("");
    console.log(`  Full report written to ${args.out}`);
  }

  console.log("");
  process.exit(report.failed.length > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
