#!/usr/bin/env node
// @ts-check
/**
 * One-time backfill of the keyed meter form onto records that carry only the aviation one (#761).
 *
 * `MeterReading` superseded six fields in #730 and #759, and every reader was moved onto a helper
 * that prefers the keyed form and falls back to the legacy one. That fallback is what lets the app
 * read existing history — and it is the thing that cannot be removed while any record still needs
 * it. This is what makes removing it safe.
 *
 *   MaintenanceLog.engine_hour / airframe_time / prop_time     -> readings
 *   MaintenanceOverview.current_*_time                         -> current  (rebuilt from the logs)
 *   MaintenanceTask.force_due_engine_hour                      -> force_due_meter
 *   ForceCompliedStatus.complied_engine_hours                  -> complied_meter
 *   InspectionRule.engine_hour_rule                            -> meter_rule
 *
 * Nothing is ever cleared and nothing keyed is ever overwritten — see the module header for why.
 *
 *   npm run legacy-meters -- --dry-run                   # report, writes nothing
 *   npm run legacy-meters                                # migrate every account
 *   npm run legacy-meters -- --only uid1,uid2            # just these accounts
 *   npm run legacy-meters -- --dry-run --out plan.json   # full report to a file
 *
 * RUN THE DRY RUN FIRST and read the counts. Idempotent — a record already carrying the keyed form
 * is skipped without a write, so re-running after a partial failure repairs only what is left.
 *
 * Credentials & project — identical to thing-dna-audit.mjs; see its header, including the
 * `$(command -v node)` requirement when running under `firebase emulators:exec`.
 */

import { writeFileSync } from "node:fs";

import { runLegacyMeterBackfill } from "../lib/migration/legacyMeterBackfill.js";

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

  const report = await runLegacyMeterBackfill({
    onlyUids: args.onlyUids,
    dryRun: args.dryRun,
  });

  console.log("");
  console.log(
    `Scanned ${report.scannedThings} things across ${report.scannedUids} accounts — ` +
      `${report.scannedLogs} logs, ${report.scannedTasks} tasks, ${report.scannedOverviews} overviews`,
  );
  console.log("");
  console.log(`  logs backfilled:        ${report.logsBackfilled}`);
  console.log(`    readings written:     ${report.logReadingsWritten}`);
  console.log(`  overviews rebuilt:      ${report.overviewsRebuilt}`);
  console.log(`  tasks backfilled:       ${report.tasksBackfilled}`);
  console.log(`    rules converted:      ${report.rulesConverted}`);
  console.log(`    forced dues:          ${report.forcedDuesConverted}`);
  console.log(`    complied statuses:    ${report.compliedStatusesConverted}`);
  console.log(`  undecodable:            ${report.undecodable.length}`);
  console.log(`  elapsed:                ${report.elapsedMs} ms`);

  if (report.undecodable.length > 0) {
    console.log("");
    console.log("Undecodable payloads — reported, never guessed at:");
    for (const entry of report.undecodable) {
      console.log(`    ${entry.uid}/${entry.thingId}/${entry.kind}/${entry.docId}`);
    }
  }

  if (args.out != null) {
    writeFileSync(args.out, JSON.stringify(report, null, 2));
    console.log("");
    console.log(`Full report written to ${args.out}`);
  }
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
