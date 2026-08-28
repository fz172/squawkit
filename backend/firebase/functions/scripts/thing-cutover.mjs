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
 *   npm run cutover -- --dry-run --bucket my-bucket    # override the bucket explicitly
 *
 * Credentials & project (Application Default Credentials; nothing is hardcoded):
 *   gcloud auth application-default login
 *   export GOOGLE_CLOUD_PROJECT=wingslog-9ca4e         # or GOOGLE_APPLICATION_CREDENTIALS=<sa.json>
 *
 * The resolved project id is printed, and flagged [EMULATOR] when one is in use, so a mis-pointed
 * credential is caught rather than silently migrating the wrong environment. A live run also asks
 * for confirmation unless `--yes` is passed; a dry run never does, because it cannot write.
 *
 * To rehearse against the emulator instead of prod:
 *
 *   npm run build
 *   NODE=$(command -v node)   # see below — this matters
 *   firebase emulators:exec --only firestore,storage --project demo-squawkit \
 *     "$NODE scripts/thing-cutover.mjs --dry-run"
 *
 * `$(command -v node)` is not decoration. The `firebase` CLI is a pkg-bundled binary carrying its
 * own Node, and a bare `node` inside `emulators:exec` resolves to that one, which cannot load an ESM
 * entry point (`ERR_REQUIRE_ESM`). Resolve the real binary in your own shell and pass the absolute
 * path. `package.json`'s `test` script does the same thing for the same reason.
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
  const args = {
    dryRun: false, yes: false, skipChecksum: false, onlyUids: [], out: null, bucket: null,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--yes") args.yes = true;
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

/**
 * The bucket to migrate.
 *
 * A local script has NO default: `adminStorage.bucket()` reads the Admin app's `storageBucket`
 * option, which the Cloud Functions runtime and `firebase emulators:exec` populate from
 * FIREBASE_CONFIG, but a process authenticated with plain ADC does not. Left unresolved it fails
 * once per account with an error that reads like a data problem — which is exactly how it first
 * showed up on a production dry run.
 *
 * Deriving `<project>.firebasestorage.app` is a convenience, not a guarantee: projects created
 * before the naming change use `<project>.appspot.com`. Whatever is chosen is printed below and
 * probed by preflight before any account is touched, so a wrong guess fails immediately and loudly
 * rather than midway through a batch.
 */
function resolvedBucket(explicit) {
  if (explicit != null && explicit.length > 0) return explicit;
  if (process.env.FIREBASE_STORAGE_BUCKET != null) return process.env.FIREBASE_STORAGE_BUCKET;
  try {
    const config = JSON.parse(process.env.FIREBASE_CONFIG ?? "{}");
    if (typeof config.storageBucket === "string" && config.storageBucket.length > 0) {
      return config.storageBucket;
    }
  } catch {
    // FIREBASE_CONFIG absent or malformed — fall through to the derivation below.
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

function formatDuration(ms) {
  const s = Math.round(ms / 1000);
  return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const emulator = process.env.FIRESTORE_EMULATOR_HOST != null;
  const bucket = resolvedBucket(args.bucket);
  console.log("");
  console.log("  Aircraft → Thing cutover");
  console.log(`  project:   ${resolvedProject()}${emulator ? "  [EMULATOR]" : ""}`);
  console.log(`  mode:      ${args.dryRun ? "DRY RUN (writes nothing)" : "LIVE (copies data)"}`);
  console.log(`  scope:     ${args.onlyUids.length > 0 ? args.onlyUids.join(", ") : "every account"}`);
  console.log(`  bucket:    ${bucket ?? "(unresolved — pass --bucket)"}`);
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
    bucketName: bucket ?? undefined,
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
