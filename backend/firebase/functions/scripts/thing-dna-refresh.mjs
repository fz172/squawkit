#!/usr/bin/env node
// @ts-check
/**
 * Refreshes each Thing's frozen template DNA to the canonical preset this repo ships (#732).
 *
 * A Thing carries its whole template inline and the client never refreshes it from the pool, so a
 * preset edit reaches only Things created after it. That is why an aeroplane created before #732
 * draws its propeller blades as a flat list while a new one draws the same blades as a compact
 * grid: same build, same screen, older bytes.
 *
 * Writes `template` wholesale from `core/template/templates/binary/*.pb`. Components are NOT
 * touched — if a Thing still carries the pre-#729 `airframe`/`hub` tree, run `npm run airplane-tree`
 * first, then this.
 *
 *   npm run dna-refresh -- --dry-run                          # report, writes nothing
 *   npm run dna-refresh -- --only <uid> --dry-run             # just this account
 *   npm run dna-refresh -- --only <uid> --templates airplane  # aeroplanes on that account
 *   npm run dna-refresh                                       # every account, every preset
 *   npm run dna-refresh -- --dry-run --out plan.json          # full report to a file
 *   npm run dna-refresh -- --inflate                          # also freeze DNA onto Things with none
 *
 * `--inflate` is off by default. A Thing carrying no DNA renders through the build's own fallback
 * and is therefore never stale; writing a copy of the current preset into it buys an identifiable
 * version at the price of a snapshot every future bump has to migrate.
 *
 * RUN THE DRY RUN FIRST and read the counts. Idempotent — a Thing whose DNA already matches the
 * canonical bytes is skipped without a write.
 *
 * Credentials & project — identical to thing-dna-audit.mjs; see its header, including the
 * `$(command -v node)` requirement when running under `firebase emulators:exec`.
 */

import { writeFileSync } from "node:fs";

import { runThingDnaRefresh } from "../lib/migration/thingDnaRefresh.js";

function parseArgs(argv) {
  const args = { onlyUids: [], onlyTemplateIds: [], out: null, dryRun: false, inflate: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--only") args.onlyUids = (argv[++i] ?? "").split(",").filter(Boolean);
    else if (arg === "--templates")
      args.onlyTemplateIds = (argv[++i] ?? "").split(",").filter(Boolean);
    else if (arg === "--out") args.out = argv[++i] ?? null;
    else if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--inflate") args.inflate = true;
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
  console.log(
    args.onlyTemplateIds.length > 0
      ? `Presets: ${args.onlyTemplateIds.join(", ")}`
      : "Presets: all",
  );
  console.log(args.dryRun ? "Mode: DRY RUN — nothing is written" : "Mode: WRITING");

  const report = await runThingDnaRefresh({
    onlyUids: args.onlyUids,
    onlyTemplateIds: args.onlyTemplateIds,
    dryRun: args.dryRun,
    inflateMissing: args.inflate,
  });

  console.log("");
  console.log(`Canonical pool: ${report.canonical.join(", ")}`);
  console.log(`Scanned ${report.scannedThings} things across ${report.scannedUids} accounts`);
  console.log(`  refreshed:         ${report.refreshed.length}`);
  console.log(`  inflated (no DNA): ${report.inflated.length}`);
  console.log(`  left without DNA:  ${report.leftWithoutDna}`);
  console.log(`  already current:   ${report.alreadyCurrent}`);
  console.log(`  unknown preset:    ${report.unknownTemplateId.length}`);
  console.log(`  filtered out:      ${report.filteredOut}`);
  console.log(`  undecodable:       ${report.undecodable.length}`);
  console.log(`  elapsed:           ${report.elapsedMs} ms`);

  for (const thing of [...report.refreshed, ...report.inflated]) {
    const name = thing.name.length > 0 ? thing.name : "(unnamed)";
    const from = thing.fromVersion == null ? "no DNA" : `v${thing.fromVersion}`;
    console.log(
      `    ${thing.uid}/${thing.thingId} ${name} — ` +
        `${thing.templateId} ${from} -> v${thing.toVersion}`,
    );
  }

  if (report.unknownTemplateId.length > 0) {
    console.log("");
    console.log("Template ids naming no shipped preset — left exactly as stored:");
    for (const entry of report.unknownTemplateId) {
      console.log(`    ${entry.uid}/${entry.thingId} — ${entry.templateId}`);
    }
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
