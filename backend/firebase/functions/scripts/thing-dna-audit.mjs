#!/usr/bin/env node
// @ts-check
/**
 * Thing DNA audit — how many Things were never given `spec` / `components`?
 *
 * **READ-ONLY. This program writes nothing**, which is the point: it exists so that #718's backfill
 * is written against a known population rather than a hypothetical one, or not written at all.
 *
 * `spec` and `components` were meant to be dual-written from Phase 1. Only the server half shipped
 * — `thingPayloads.ts` populates both during the cutover, and until #717 the client never wrote
 * either. So a Thing created *after* its account was migrated has neither. How many exist is
 * unknown, and may be zero.
 *
 *   npm run dna-audit                          # every account, summary to stdout
 *   npm run dna-audit -- --only uid1,uid2      # just these accounts
 *   npm run dna-audit -- --out dna-audit.json  # full report, every affected Thing
 *
 * READ THE NUMBER AS A FLOOR UNTIL THE #717 BUILD HAS SHIPPED. #717 inflates on write, but only on
 * devices running a build that contains it; every older client still writes Things without DNA. A
 * count taken before that release has reached devices will keep growing. Re-run afterwards, and
 * treat *that* number as the population the backfill has to repair.
 *
 * Credentials & project — identical to thing-cutover.mjs; see its header, including the
 * `$(command -v node)` requirement when running under `firebase emulators:exec`.
 */

import { writeFileSync } from "node:fs";

import { runThingDnaAudit } from "../lib/migration/thingDnaAudit.js";

function parseArgs(argv) {
  const args = { onlyUids: [], out: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--only") args.onlyUids = (argv[++i] ?? "").split(",").filter(Boolean);
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
    "(unset)"
  );
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  console.log(`Project: ${resolvedProject()}`);
  console.log(
    args.onlyUids.length > 0 ? `Accounts: ${args.onlyUids.join(", ")}` : "Accounts: all",
  );
  console.log("Read-only — this program writes nothing.\n");

  const report = await runThingDnaAudit({ onlyUids: args.onlyUids });

  console.log(`Scanned ${report.scannedThings} Things across ${report.scannedUids} accounts `
    + `in ${report.elapsedMs}ms\n`);

  if (report.needingDna.length === 0) {
    console.log("✅ No Thing is missing spec/components.");
    console.log("   If the #717 build has already reached every device, #718 has nothing to");
    console.log("   repair and can close as verified-empty. If it has not, this is a floor —");
    console.log("   re-run after the release.");
  } else {
    console.log(`⚠️  ${report.needingDna.length} Thing(s) have no components — the backfill set:`);
    const byUid = new Map();
    for (const entry of report.needingDna) {
      byUid.set(entry.uid, (byUid.get(entry.uid) ?? 0) + 1);
    }
    for (const [uid, count] of byUid) console.log(`      ${uid}: ${count}`);
  }

  if (report.missingTemplateOnly.length > 0) {
    console.log(`\n${report.missingTemplateOnly.length} Thing(s) have components but no template.`);
    console.log("   Not a backfill target: absent DNA resolves to airplane over a closed set, and");
    console.log("   #717 fills it on the next write. Listed so the two cases stay distinguishable.");
  }

  if (report.undecodable.length > 0) {
    console.log(`\n❗ ${report.undecodable.length} payload(s) would not decode. Reported, not guessed at:`);
    for (const entry of report.undecodable) console.log(`      ${entry.uid}/${entry.thingId}`);
  }

  if (args.out != null) {
    writeFileSync(args.out, `${JSON.stringify(report, null, 2)}\n`);
    console.log(`\nFull report written to ${args.out}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
