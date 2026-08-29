#!/usr/bin/env node
// @ts-check
/**
 * ACL tree cutover — Phase G, tasks G1/G2 (thing_migration_design.md §5.4).
 *
 * Copies `aircraft_shares/{hostUid}/aircraft/{acId}` and its `members/*` / `invites/*`
 * subcollections to `thing_shares/{hostUid}/thing/{acId}`, then verifies each copy.
 *
 * **Copy only — it never deletes.** Removing the old tree is G6, after its own 7-day grace window,
 * and only once a client release reading the new location has reached every device (G4/G5).
 *
 * SAFE TO RUN NOW, and inert when you do. The `thing_shares` rules block has been live since C1,
 * but `shareRole()` still reads `aircraft_shares`, so nothing authorizes against the copy until G3
 * repoints it. That is the whole reason this can run early and unhurried: the copy is dead data
 * until three things deploy together — the `sharingModels.ts` constants (B6), the `shareRole()`
 * repoint, and a client release updating `SharingManagerImpl`'s constants.
 *
 *   npm run share-cutover -- --dry-run                  # count; write nothing
 *   npm run share-cutover -- --yes                      # copy every host with a share
 *   npm run share-cutover -- --only host1,host2 --yes   # a subset
 *   npm run share-cutover -- --yes --out shares.json
 *
 * Credentials & project: identical to thing-cutover.mjs — see its header, including the
 * `$(command -v node)` requirement under `firebase emulators:exec`.
 */

import { writeFileSync } from "node:fs";
import { createInterface } from "node:readline/promises";

import { runThingShareCutover } from "../lib/migration/thingShareCutover.js";

function parseArgs(argv) {
  const args = { dryRun: false, yes: false, onlyHosts: [], out: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--yes") args.yes = true;
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
  console.log("  ACL tree cutover  (aircraft_shares → thing_shares)");
  console.log(`  project:   ${resolvedProject()}${emulator ? "  [EMULATOR]" : ""}`);
  console.log(`  mode:      ${args.dryRun ? "DRY RUN (writes nothing)" : "LIVE (copies data)"}`);
  console.log(`  scope:     ${args.onlyHosts.length > 0 ? args.onlyHosts.join(", ") : "every host"}`);
  console.log("");

  if (!args.dryRun && !args.yes) {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    const answer = await rl.question("  Copy the ACL tree in the project above? [y/N] ");
    rl.close();
    if (answer.trim().toLowerCase() !== "y") {
      console.log("  Aborted.");
      process.exit(1);
    }
  }

  const report = await runThingShareCutover({ dryRun: args.dryRun, onlyHosts: args.onlyHosts });

  const t = report.totals;
  console.log("");
  console.log(`  hosts:     ${report.succeeded.length} ok, ${report.failed.length} failed`);
  console.log(`  shares:    ${t.sharesCopied}`);
  console.log(`  members:   ${t.membersCopied}`);
  console.log(`  invites:   ${t.invitesCopied}`);

  if (report.mismatched.length > 0) {
    // The gate on G3. A mismatch here means the copy is not a faithful replica, and repointing
    // shareRole() at it would revoke access for whoever is missing.
    console.log("");
    console.log("  MISMATCHED (do NOT proceed to G3):");
    for (const m of report.mismatched) {
      console.log(`    ${m.hostUid}/${m.acId}  ${m.reason}`);
    }
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
  process.exit(report.failed.length > 0 || report.mismatched.length > 0 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
