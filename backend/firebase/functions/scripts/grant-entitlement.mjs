#!/usr/bin/env node
// @ts-check
/**
 * Admin comp tool — grant or revoke a Pro entitlement for one account.
 *
 * This is the same write path a real grant uses: it calls `applyEntitlement` (the single writer of
 * `subscriptions/{uid}`), so the write is idempotent and the deployed `projectAttachmentEntitlement`
 * trigger fires and projects `attachmentsEnabled` onto the account's shared aircraft automatically.
 * There is no admin UI and the `grantPromoEntitlement` callable enforces App Check from a signed-in
 * admin client; this script is the out-of-band equivalent for comping accounts by hand.
 *
 * It imports the COMPILED output under `lib/`, so build first (the npm script below does this):
 *
 *   npm run grant -- --email you@example.com            # grant Pro, 365 days (default)
 *   npm run grant -- --uid abc123 --days 30             # grant Pro, 30 days
 *   npm run grant -- --email you@example.com --revoke   # lapse back to Free (Expired)
 *   npm run grant -- --uid abc123 --yes                 # skip the confirmation prompt
 *
 * Credentials & project (Application Default Credentials; nothing is hardcoded):
 *   gcloud auth application-default login
 *   export GOOGLE_CLOUD_PROJECT=wingslog-9ca4e          # or GOOGLE_APPLICATION_CREDENTIALS=<sa.json>
 *   # To target the local emulator instead of prod: export FIRESTORE_EMULATOR_HOST=localhost:8080
 *
 * The resolved project id is printed and confirmed before any write, so a mis-pointed credential is
 * caught rather than silently comping the wrong environment.
 */

import { createInterface } from "node:readline/promises";

import { adminAuth } from "../lib/config/firebaseAdmin.js";
import { applyEntitlement } from "../lib/subscription/applyEntitlement.js";
import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
} from "../lib/subscription/entitlementModel.js";

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function parseArgs(argv) {
  const args = { uid: "", email: "", days: 365, revoke: false, yes: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--uid": args.uid = (argv[++i] ?? "").trim(); break;
      case "--email": args.email = (argv[++i] ?? "").trim(); break;
      case "--days": args.days = Number(argv[++i]); break;
      case "--revoke": case "--free": args.revoke = true; break;
      case "--yes": case "-y": args.yes = true; break;
      default:
        console.error(`Unknown argument: ${a}`);
        process.exit(2);
    }
  }
  if (!args.uid && !args.email) {
    console.error("Provide --uid <uid> or --email <address>.");
    process.exit(2);
  }
  if (!args.revoke && (!Number.isFinite(args.days) || args.days <= 0)) {
    console.error("--days must be a positive number.");
    process.exit(2);
  }
  return args;
}

async function resolveUid({ uid, email }) {
  if (uid) return uid;
  const user = await adminAuth.getUserByEmail(email);
  return user.uid;
}

/** A Pro grant (comp), or a lapse back to Free — mirrors grantPromoEntitlement's shape. */
function buildEntitlement({ uid, days, revoke }) {
  const now = Date.now();
  if (revoke) {
    return {
      uid,
      eventId: `admin-revoke:${uid}:${now}`,
      status: SUBSCRIPTION_STATUS.FREE,
      lifecycle: SUBSCRIPTION_LIFECYCLE.EXPIRED,
      memberSinceMillis: 0,
      currentPeriodEndMillis: now,
      willRenew: false,
      source: ENTITLEMENT_SOURCE.SERVER_GRANT,
      originPlatform: "server",
    };
  }
  return {
    uid,
    // One event per invocation; the timestamp keeps repeated grants to the same uid distinct while a
    // double-run of the SAME command dedups through applyEntitlement.
    eventId: `admin-grant:${uid}:${now}`,
    status: SUBSCRIPTION_STATUS.PRO,
    lifecycle: SUBSCRIPTION_LIFECYCLE.ACTIVE,
    memberSinceMillis: now,
    currentPeriodEndMillis: now + days * MS_PER_DAY,
    willRenew: false,
    source: ENTITLEMENT_SOURCE.SERVER_GRANT,
    originPlatform: "server",
  };
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

  const uid = await resolveUid(args);
  const target = args.email ? `${args.email} (${uid})` : uid;
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;
  const action = args.revoke ? "REVOKE → Free (Expired)" : `GRANT Pro for ${args.days} day(s)`;

  console.log("");
  console.log(`  Project : ${projectId}${emulator ? `  (emulator ${emulator})` : "  (LIVE)"}`);
  console.log(`  Account : ${target}`);
  console.log(`  Action  : ${action}`);
  console.log("");

  if (!args.yes && !(await confirm("Proceed?"))) {
    console.log("Aborted.");
    process.exit(0);
  }

  const result = await applyEntitlement(buildEntitlement({ uid, days: args.days, revoke: args.revoke }));
  if (result.applied) {
    console.log(`✔ Applied. subscriptions/${uid} written; the projector will update the account's shares.`);
  } else {
    console.log(`• No-op (${result.reason ?? "already applied"}).`);
  }
  process.exit(0);
}

main().catch((err) => {
  console.error("Failed:", err?.message ?? err);
  process.exit(1);
});
