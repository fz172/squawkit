#!/usr/bin/env node
// @ts-check
/**
 * ACL tree deletion — Phase G, task G6 (thing_migration_design.md §5.4 step 4).
 *
 * Deletes `aircraft_shares/{hostUid}/aircraft/{acId}` and its `members/*` / `invites/*`
 * subcollections, once the replica at `thing_shares/{hostUid}/thing/{acId}` has been re-proven
 * complete.
 *
 * WHAT AUTHORISES A DELETE IS RE-VERIFICATION, NOT ELAPSED TIME. G1/G2 verified the copy when they
 * ran, but that was days earlier and shares are not static — a member joins, a role changes, an
 * invite is minted. So every share is re-checked against live data first: the root document
 * present, every `memberRoles` entry matching, and every member and invite document present **by
 * id** (equal counts with different ids would pass a count check while having lost a collaborator).
 * Anything that fails is skipped and reported, never deleted.
 *
 * The 7-day window (G5) is the separate, human half: it is about every device having picked up the
 * G3 client, because an un-updated one still reads this tree. Asserted with `--grace-elapsed`,
 * since nothing stores a per-device distribution timestamp to check it against.
 *
 *   npm run share-cleanup -- --dry-run                    # report what would go; delete nothing
 *   npm run share-cleanup -- --grace-elapsed --yes        # the real thing
 *   npm run share-cleanup -- --only host1 --grace-elapsed --yes
 *   npm run share-cleanup -- --dry-run --out cleanup.json # full report, every skip reason
 *
 * Credentials & project: identical to thing-cutover.mjs — see its header, including the
 * `$(command -v node)` requirement under `firebase emulators:exec`. No bucket is needed; this
 * touches Firestore only.
 *
 * ORDERING: only after G5's window has elapsed since the G3 client reached every device. Until
 * then the old tree is what un-updated clients read, and this is the program that removes it.
 */

import { writeFileSync } from "node:fs";
import { createInterface } from "node:readline/promises";

import { runThingShareCleanup } from "../lib/migration/thingShareCleanup.js";

function parseArgs(argv) {
  const args = { dryRun: false, yes: false, graceElapsed: false, onlyHosts: [], out: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--yes") args.yes = true;
    else if (arg === "--grace-elapsed") args.graceElapsed = true;
    else if (arg === "--only") args.onlyHosts = (argv[++i] ?? "").split(",").filter(Boolean);
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const emulator = process.env.FIRESTORE_EMULATOR_HOST != null;

  console.log("");
  console.log("  ACL tree deletion  (DELETES aircraft_shares)");
  console.log(`  project:   ${resolvedProject()}${emulator ? "  [EMULATOR]" : ""}`);
  console.log(`  mode:      ${args.dryRun ? "DRY RUN (deletes nothing)" : "LIVE (DELETES DATA)"}`);
  console.log(`  scope:     ${args.onlyHosts.length > 0 ? args.onlyHosts.join(", ") : "every host"}`);
  console.log("");

  if (!args.dryRun && !args.yes) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    console.log("  This permanently deletes the aircraft_shares ACL tree.");
    console.log("  Any client still reading it loses its share roster.");
    const answer = await rl.question("  Type DELETE to proceed: ");
    rl.close();
    if (answer.trim() !== "DELETE") {
      console.log("  Aborted.");
      process.exit(1);
    }
  }

  const report = await runThingShareCleanup({
    dryRun: args.dryRun,
    onlyHosts: args.onlyHosts,
    graceElapsed: args.graceElapsed,
  });

  const t = report.totals;
  console.log("");
  console.log(`  hosts:      ${report.succeeded.length} ok, ${report.failed.length} failed`);
  console.log(`  shares:     ${t.sharesDeleted} deleted, ${t.sharesSkipped} skipped`);
  console.log(`  documents:  ${t.documentsDeleted}`);

  if (report.skipped.length > 0) {
    // Primary output, not a footnote: each skip is a share whose replica could not be re-proven,
    // which is something to go and look at rather than run past.
    console.log("");
    console.log("  SKIPPED (left in place — replica could not be re-verified):");
    for (const s of report.skipped) console.log(`    ${s.hostUid}/${s.acId}  ${s.reason}`);
  }

  if (report.failed.length > 0) {
    console.log("");
    console.log("  FAILED:");
    for (const { hostUid, error } of report.failed) console.log(`    ${hostUid}  ${error}`);
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
