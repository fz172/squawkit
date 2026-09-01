#!/usr/bin/env node
// @ts-check
/**
 * One-time repair of airplane component trees to the shape the template declares (#729).
 *
 * Phase 1's cutover built `airframe -> engine -> propeller -> (hub, blade)`. The template now says
 * `engine -> propeller -> blade`: the airframe *is* the thing, so its identity lives in `spec`, and
 * a propeller's make/model/serial ARE the hub's. A Thing's DNA is authoritative at render, so one
 * left in the old shape walks the new slots and matches nothing — its engines stay in storage and
 * stop being drawn.
 *
 * Writes both halves of each document: the restructured `components`, and the `component_slots` in
 * its DNA, because the DNA is what the client walks. Nothing else in the template is touched.
 *
 *   npm run airplane-tree -- --dry-run                   # report, writes nothing
 *   npm run airplane-tree                                # migrate every account
 *   npm run airplane-tree -- --only uid1,uid2            # just these accounts
 *   npm run airplane-tree -- --dry-run --out plan.json   # full report to a file
 *
 * RUN THE DRY RUN FIRST and read the counts. Idempotent — a Thing already in the new shape is
 * skipped without a write, so re-running after a partial failure repairs only what is left.
 *
 * Credentials & project — identical to thing-dna-audit.mjs; see its header, including the
 * `$(command -v node)` requirement when running under `firebase emulators:exec`.
 */

import { writeFileSync } from "node:fs";

import { runAirplaneTreeMigration } from "../lib/migration/airplaneTreeMigration.js";

function parseArgs(argv) {
  const args = { onlyUids: [], out: null, dryRun: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--only") args.onlyUids = (argv[++i] ?? "").split(",").filter(Boolean);
    else if (arg === "--out") args.out = argv[++i] ?? null;
    else if (arg === "--dry-run") args.dryRun = true;
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
    "(unset)"
  );
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  console.log(`Project: ${resolvedProject()}`);
  console.log(args.onlyUids.length > 0 ? `Accounts: ${args.onlyUids.join(", ")}` : "Accounts: all");
  console.log(args.dryRun ? "Mode: DRY RUN — nothing is written" : "Mode: WRITING");

  const report = await runAirplaneTreeMigration({
    onlyUids: args.onlyUids,
    dryRun: args.dryRun,
  });

  console.log("");
  console.log(`Scanned ${report.scannedThings} things across ${report.scannedUids} accounts`);
  console.log(`  restructured:      ${report.migrated.length}`);
  console.log(`  already migrated:  ${report.alreadyMigrated}`);
  console.log(`  no components:     ${report.skippedNonAirplane}`);
  console.log(`  undecodable:       ${report.undecodable.length}`);
  console.log(`  elapsed:           ${report.elapsedMs} ms`);

  for (const thing of report.migrated) {
    const name = thing.name.length > 0 ? thing.name : "(unnamed)";
    console.log(
      `    ${thing.uid}/${thing.thingId} ${name} — ` +
        `${thing.enginesLifted} engine(s) lifted, ${thing.hubsFolded} hub(s) folded`,
    );
  }

  if (report.undecodable.length > 0) {
    console.log("");
    console.log("Undecodable payloads — reported, never guessed at:");
    for (const entry of report.undecodable) {
      console.log(`    ${entry.uid}/${entry.thingId}`);
    }
  }

  if (args.out != null) {
    writeFileSync(args.out, JSON.stringify(report, null, 2));
    console.log(`\nFull report written to ${args.out}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
