#!/usr/bin/env node
// @ts-check
/**
 * Promo-code pool tool (#750) — mint a batch of codes, and report on what has been spent.
 *
 * Minting is deliberately out-of-band. A code is worth real money, and there is no admin UI; a
 * script run against Application Default Credentials by whoever holds them is the whole access
 * control, exactly as `grant-entitlement.mjs` is for a one-off comp. Redemption, by contrast, is a
 * callable any signed-in pilot may reach — the asymmetry is the point.
 *
 * It imports the COMPILED output under `lib/`, so build first (the npm script below does this):
 *
 *   npm run promo -- generate --count 50 --duration 1y --batch oshkosh-2026
 *   npm run promo -- generate --count 10 --days 45 --batch beta --expires-days 90 --out codes.csv
 *   npm run promo -- report --batch oshkosh-2026
 *   npm run promo -- report                      # every batch
 *
 * Durations: --duration 1m | 3m | 1y (30 / 90 / 365 days), or --days <n> for anything else.
 * --expires-days bounds how long a code may be REDEEMED; it is unrelated to how much Pro it buys.
 *
 * Credentials & project (Application Default Credentials; nothing is hardcoded):
 *   gcloud auth application-default login
 *   export GOOGLE_CLOUD_PROJECT=wingslog-9ca4e          # or GOOGLE_APPLICATION_CREDENTIALS=<sa.json>
 *   # To target the local emulator instead of prod: export FIRESTORE_EMULATOR_HOST=localhost:8080
 *
 * The resolved project id is printed and confirmed before any write, so a mis-pointed credential is
 * caught rather than silently minting a year of Pro into production.
 */

import { writeFileSync } from "node:fs";
import { createInterface } from "node:readline/promises";

import { adminAuth, adminDb } from "../lib/config/firebaseAdmin.js";
import {
  formatPromoCode,
  generatePromoCode,
  promoCodeDocPath,
  PROMO_CODES_COLLECTION,
  PROMO_DURATIONS,
} from "../lib/subscription/promoCodes.js";

const MS_PER_DAY = 24 * 60 * 60 * 1000;
/** Firestore's hard cap on a single batched write. */
const WRITE_BATCH_LIMIT = 500;

function usage(message) {
  if (message) console.error(`${message}\n`);
  console.error(
    [
      "Usage:",
      "  promo-codes.mjs generate --count <n> (--duration 1m|3m|1y | --days <n>) --batch <label>",
      "                           [--expires-days <n>] [--out <file.csv>] [--yes]",
      "  promo-codes.mjs report [--batch <label>]",
    ].join("\n"),
  );
  process.exit(2);
}

function parseArgs(argv) {
  const command = argv[0];
  if (command !== "generate" && command !== "report") {
    usage(command ? `Unknown command: ${command}` : "A command is required.");
  }
  const args = {
    command,
    count: 0,
    days: 0,
    duration: "",
    batch: "",
    expiresDays: 0,
    out: "",
    yes: false,
  };
  for (let i = 1; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--count": args.count = Number(argv[++i]); break;
      case "--days": args.days = Number(argv[++i]); break;
      case "--duration": args.duration = (argv[++i] ?? "").trim(); break;
      case "--batch": args.batch = (argv[++i] ?? "").trim(); break;
      case "--expires-days": args.expiresDays = Number(argv[++i]); break;
      case "--out": args.out = (argv[++i] ?? "").trim(); break;
      case "--yes": case "-y": args.yes = true; break;
      default: usage(`Unknown argument: ${a}`);
    }
  }

  if (command === "report") return args;

  if (!Number.isInteger(args.count) || args.count <= 0) {
    usage("--count must be a positive integer.");
  }
  if (args.duration) {
    const days = PROMO_DURATIONS[args.duration];
    if (days == null) {
      usage(`--duration must be one of: ${Object.keys(PROMO_DURATIONS).join(", ")}`);
    }
    args.days = days;
  }
  if (!Number.isFinite(args.days) || args.days <= 0) {
    usage("Give a term: --duration 1m|3m|1y, or --days <n>.");
  }
  // Required, not defaulted: an unlabelled batch cannot be counted or withdrawn later, and the
  // moment to decide what to call a run is when you mint it.
  if (args.batch.length === 0) usage("--batch <label> is required.");
  if (args.expiresDays !== 0 && (!Number.isFinite(args.expiresDays) || args.expiresDays <= 0)) {
    usage("--expires-days must be a positive number.");
  }
  return args;
}

async function confirm(question) {
  if (!process.stdin.isTTY) {
    console.error("Not a TTY; re-run with --yes to confirm non-interactively.");
    process.exit(1);
  }
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const answer = (await rl.question(`${question} [y/N] `)).trim().toLowerCase();
  rl.close();
  return answer === "y" || answer === "yes";
}

/**
 * Mints [count] distinct codes.
 *
 * Uniqueness is checked against the generated set only, not against Firestore. At 59 bits a
 * collision with an existing code is vanishingly unlikely, and the write below uses `create`, which
 * fails loudly rather than overwriting one — so the unlikely case is a failed run, not a code
 * silently reassigned out from under whoever holds it.
 */
function mintCodes(count) {
  const codes = new Set();
  while (codes.size < count) codes.add(generatePromoCode());
  return [...codes];
}

async function generate(args, projectId) {
  const now = Date.now();
  const expiresAtMillis = args.expiresDays > 0 ? now + args.expiresDays * MS_PER_DAY : 0;
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;

  console.log("");
  console.log(`  Project : ${projectId}${emulator ? `  (emulator ${emulator})` : "  (LIVE)"}`);
  console.log(`  Action  : MINT ${args.count} promo code(s), ${args.days} day(s) of Pro each`);
  console.log(`  Batch   : ${args.batch}`);
  console.log(
    `  Expires : ${expiresAtMillis === 0 ? "never" : new Date(expiresAtMillis).toISOString()}`,
  );
  console.log("");

  if (!args.yes && !(await confirm("Proceed?"))) {
    console.log("Aborted.");
    process.exit(0);
  }

  const codes = mintCodes(args.count);
  for (let i = 0; i < codes.length; i += WRITE_BATCH_LIMIT) {
    const chunk = codes.slice(i, i + WRITE_BATCH_LIMIT);
    const batch = adminDb.batch();
    for (const code of chunk) {
      // create, not set: a collision with an existing code must fail the run, never overwrite it.
      batch.create(adminDb.doc(promoCodeDocPath(code)), {
        durationDays: args.days,
        batch: args.batch,
        createdAtMillis: now,
        expiresAtMillis,
        redeemedByUid: null,
        redeemedAtMillis: null,
        grantedUntilMillis: null,
      });
    }
    await batch.commit();
  }

  const formatted = codes.map(formatPromoCode);
  if (args.out) {
    const csv = ["code,batch,duration_days\n"]
      .concat(formatted.map((c) => `${c},${args.batch},${args.days}\n`))
      .join("");
    writeFileSync(args.out, csv);
    console.log(`✔ Minted ${codes.length} code(s) → ${args.out}`);
  } else {
    console.log(`✔ Minted ${codes.length} code(s):`);
    formatted.forEach((c) => console.log(`  ${c}`));
  }
}

async function report(args) {
  let query = adminDb.collection(PROMO_CODES_COLLECTION);
  if (args.batch) query = query.where("batch", "==", args.batch);
  const snap = await query.get();

  /** @type {Map<string, {total: number, redeemed: number, days: number}>} */
  const batches = new Map();
  const redeemed = [];
  for (const doc of snap.docs) {
    const d = doc.data();
    const entry = batches.get(d.batch) ?? { total: 0, redeemed: 0, days: d.durationDays };
    entry.total += 1;
    if (d.redeemedByUid) {
      entry.redeemed += 1;
      redeemed.push({
        code: formatPromoCode(doc.id),
        batch: d.batch,
        uid: d.redeemedByUid,
        at: d.redeemedAtMillis ? new Date(d.redeemedAtMillis).toISOString() : "",
        until: d.grantedUntilMillis ? new Date(d.grantedUntilMillis).toISOString() : "",
      });
    }
    batches.set(d.batch, entry);
  }

  if (batches.size === 0) {
    console.log("No promo codes found.");
    return;
  }
  console.log("");
  for (const [batch, { total, redeemed: used, days }] of [...batches].sort()) {
    console.log(`  ${batch}: ${used}/${total} redeemed  (${days} day(s) of Pro each)`);
  }
  if (redeemed.length > 0) {
    console.log("");
    console.log("  Redeemed:");
    redeemed
      .sort((a, b) => a.at.localeCompare(b.at))
      .forEach((r) => console.log(`    ${r.code}  ${r.uid}  ${r.at} → ${r.until}`));
  }
  console.log("");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const projectId = adminAuth.app.options.projectId ?? process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) {
    console.error(
      "No project id resolved. Set GOOGLE_CLOUD_PROJECT (or GOOGLE_APPLICATION_CREDENTIALS to a " +
        "service-account key) and try again.",
    );
    process.exit(1);
  }

  if (args.command === "generate") {
    await generate(args, projectId);
  } else {
    await report(args);
  }
  process.exit(0);
}

main().catch((err) => {
  console.error("Failed:", err?.message ?? err);
  process.exit(1);
});
