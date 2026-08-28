#!/usr/bin/env node
// @ts-check
/**
 * Aircraft → Thing cutover — the Phase D batch (thing_migration_design.md §5.1, tasks B1–B3).
 *
 * Copies every account's entity tree from `/users/{uid}/aircraft/...` to `/users/{uid}/thing/...`,
 * copies the attachment blobs that go with it, and performs the two payload rewrites on the way
 * through: the embedded `Attachment.storage_path`, and the `template_id`/`name`/`spec`/`components`
 * backfill on each Thing.
 *
 * **It never deletes.** The old documents and objects stay put; reclaiming them is a separate pass
 * after the 7-day grace window. That is what makes re-running this safe, which matters because
 * re-running IS the recovery path: each uid is isolated in its own try/catch, and the run ends with
 * a report naming which failed. Re-run with `--only` until a run reports zero failures — that report
 * is the gate on shipping the client build (E2) and on the ACL cutover (Phase G).
 *
 * It imports the COMPILED output under `lib/`, so build first (the npm script below does this):
 *
 *   npm run cutover -- --dry-run                       # read, verify, count; write nothing
 *   npm run cutover -- --yes                           # the real run, every account
 *   npm run cutover -- --only uid1,uid2 --yes          # retry just these
 *   npm run cutover -- --dry-run --skip-checksum       # size-only blob verification (faster)
 *   npm run cutover -- --yes --out report.json         # also write the full report to a file
 *
 * Credentials & project (Application Default Credentials; nothing is hardcoded):
 *   gcloud auth application-default login
 *   export GOOGLE_CLOUD_PROJECT=wingslog-9ca4e         # or GOOGLE_APPLICATION_CREDENTIALS=<sa.json>
 *   # To target the local emulator instead of prod:
 *   export FIRESTORE_EMULATOR_HOST=localhost:8080
 *   export FIREBASE_STORAGE_EMULATOR_HOST=localhost:9199
 *
 * The resolved project id is printed and confirmed before any write, so a mis-pointed credential is
 * caught rather than silently migrating the wrong environment.
 *
 * ORDERING — this is not a script to run whenever. Per §2.7c and §5.2:
 *   1. C1 must already be deployed (the `/thing/` rules block), or the copies are unreadable.
 *   2. The `/thing/` Cloud Function triggers must NOT be deployed yet. A copy creates each document,
 *      so every copied record would look like a fresh write to them — firing blob GC against a
 *      half-populated tree and sending one push per record per share member. They ship at C2, after
 *      this finishes.
 */

import { writeFileSync } from "node:fs";
import { createInterface } from "node:readline/promises";

import { runThingCutover } from "../lib/migration/thingCutover.js";

function parseArgs(argv) {
  const args = { dryRun: false, yes: false, skipChecksum: false, onlyUids: [], out: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--yes") args.yes = true;
    else if (arg === "--skip-checksum") args.skipChecksum = true;
    else if (arg === "--only") args.onlyUids = (argv[++i] ?? "").split(",").filter(Boolean);
    else if (arg === "--out") args.out = argv[++i] ?? null;
    else {
      console.error(`Unknown argument: ${arg}`);
      process.exit(2);
    }
  }
  return args;
}

function resolvedProject() {
  return (
    process.env.GOOGLE_CLOUD_PROJECT ??
    process.env.GCLOUD_PROJECT ??
    process.env.FIREBASE_PROJECT ??
    "(unset — Admin SDK will infer from GOOGLE_APPLICATION_CREDENTIALS)"
  );
}

function formatDuration(ms) {
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const emulator = process.env.FIRESTORE_EMULATOR_HOST != null;
  console.log("");
  console.log("  Aircraft → Thing cutover");
  console.log(`  project:   ${resolvedProject()}${emulator ? "  [EMULATOR]" : ""}`);
  console.log(`  mode:      ${args.dryRun ? "DRY RUN (writes nothing)" : "LIVE (copies data)"}`);
  console.log(`  scope:     ${args.onlyUids.length > 0 ? args.onlyUids.join(", ") : "every account"}`);
  console.log(`  blob check: ${args.skipChecksum ? "size only" : "size + sha256"}`);
  console.log("");

  if (!args.dryRun && !args.yes) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    const answer = await rl.question("  Copy data in the project above? [y/N] ");
    rl.close();
    if (answer.trim().toLowerCase() !== "y") {
      console.log("  Aborted.");
      process.exit(1);
    }
  }

  const report = await runThingCutover({
    dryRun: args.dryRun,
    onlyUids: args.onlyUids,
    skipChecksum: args.skipChecksum,
  });

  const t = report.totals;
  console.log("");
  console.log(`  Finished in ${formatDuration(report.finishedAtMs - report.startedAtMs)}`);
  console.log(`  accounts:   ${report.succeeded.length} ok, ${report.failed.length} failed`);
  console.log(`  things:     ${t.thingsCopied} copied, ${t.thingsBackfilled} backfilled`);
  console.log(`  records:    ${t.recordsCopied} copied, ${t.storagePathsRewritten} paths rewritten`);
  console.log(
    `  blobs:      ${t.blobsCopied} copied (${t.blobBytesCopied} bytes), ` +
      `${t.blobsAlreadyPresent} already present`,
  );

  if (report.failed.length > 0) {
    console.log("");
    console.log("  FAILED:");
    for (const { uid, error } of report.failed) console.log(`    ${uid}  ${error}`);
    console.log("");
    console.log("  Retry just these with:");
    console.log(`    npm run cutover -- --only ${report.failed.map((f) => f.uid).join(",")} --yes`);
  }

  if (args.out != null) {
    writeFileSync(args.out, JSON.stringify(report, null, 2));
    console.log("");
    console.log(`  Full report written to ${args.out}`);
  }

  console.log("");
  // A non-zero exit on any failure, so a CI or shell caller cannot mistake a partial run for a
  // clean one. Zero failures across every uid is the gate this script exists to produce.
  process.exit(report.failed.length > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
