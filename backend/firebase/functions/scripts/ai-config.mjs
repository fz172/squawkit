#!/usr/bin/env node
// @ts-check
/**
 * Admin tool for `ai_config/global`, the AI backend's config and kill switch
 * (docs/ai/task_population_design.md §4.3, §5.3).
 *
 *   npm run ai-config                      # print the live config; create it from the defaults if absent
 *   npm run ai-config -- --enabled true    # turn AI runs on (the kill switch), nothing else changes
 *   npm run ai-config -- --enabled false   # turn them off
 *   npm run ai-config -- --yes             # skip the confirmation prompt
 *
 * Seeding never overwrites: a config that exists is a decision someone made, and the defaults are
 * placeholders. Ceilings and providers are edited in the console until a flag here needs to exist.
 *
 * It imports the COMPILED output under `lib/`, so build first (the npm script does this).
 * Credentials and project resolve as in grant-entitlement.mjs; set FIRESTORE_EMULATOR_HOST to
 * target the emulator.
 */

import { createInterface } from "node:readline/promises";

import { requireProjectId } from "./projectId.mjs";

function parseArgs(argv) {
  /** @type {{ enabled: boolean | null, yes: boolean }} */
  const args = { enabled: null, yes: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--enabled": {
        const v = argv[++i];
        if (v !== "true" && v !== "false") {
          console.error("--enabled takes true or false.");
          process.exit(2);
        }
        args.enabled = v === "true";
        break;
      }
      case "--yes": case "-y": args.yes = true; break;
      default:
        console.error(`Unknown argument: ${a}`);
        process.exit(2);
    }
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const projectId = requireProjectId();
  const { adminDb } = await import("../lib/config/firebaseAdmin.js");
  const { AI_CONFIG_DOC_PATH, DEFAULT_AI_CONFIG, parseAiConfig } = await import("../lib/ai/collections.js");

  const ref = adminDb.doc(AI_CONFIG_DOC_PATH);
  const snap = await ref.get();
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;

  console.log("");
  console.log(`  Project : ${projectId}${emulator ? `  (emulator ${emulator})` : "  (LIVE)"}`);
  console.log(`  Current : ${snap.exists ? JSON.stringify(snap.data()) : "(absent)"}`);
  if (snap.exists && !parseAiConfig(snap.data()).enabled && snap.get("enabled") === true) {
    console.log("  Warning : the document is malformed, so functions read it as disabled.");
  }

  const seed = !snap.exists;
  const flip = args.enabled !== null && (!snap.exists || snap.get("enabled") !== args.enabled);
  if (!seed && !flip) {
    console.log("");
    console.log("• Nothing to change.");
    process.exit(0);
  }

  const next = seed
    ? { ...DEFAULT_AI_CONFIG, enabled: args.enabled ?? DEFAULT_AI_CONFIG.enabled }
    : { enabled: args.enabled };
  console.log(`  Action  : ${seed ? "CREATE" : "UPDATE"} ${AI_CONFIG_DOC_PATH} ← ${JSON.stringify(next)}`);
  console.log("");

  if (!args.yes && !(await confirm("Proceed?"))) {
    console.log("Aborted.");
    process.exit(0);
  }

  // create() fails if someone seeded in the meantime, rather than overwriting them.
  if (seed) await ref.create(next);
  else await ref.update(next);
  console.log(`✔ ${AI_CONFIG_DOC_PATH} written.`);
  process.exit(0);
}

main().catch((err) => {
  console.error("Failed:", err?.message ?? err);
  process.exit(1);
});
