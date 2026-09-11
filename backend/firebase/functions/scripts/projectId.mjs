// @ts-check
/**
 * Resolves which Firebase project an admin script should act on.
 *
 * `initializeApp()` with no options cannot work this out on its own under `gcloud auth
 * application-default login`: that credential is a refresh token, which carries no project id, so
 * `app.options.projectId` is `undefined` and every Firestore call fails with "Unable to detect a
 * Project Id". Admin SDK's own fallbacks are the two env vars below and nothing else.
 *
 * So the last fallback is `.firebaserc` — the repo's own statement of which project it deploys to,
 * and the same file the `firebase` CLI reads. That is a lookup, not a hardcoded id: a checkout
 * pointed at a different project resolves differently, and every script still prints what it
 * resolved and asks before writing, so a mis-pointed credential is still caught by a human.
 */

import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/** backend/firebase/.firebaserc — two levels up from `functions/scripts/`. */
const FIREBASERC = resolve(dirname(fileURLToPath(import.meta.url)), "../../.firebaserc");

/**
 * The project id, or "" if nothing names one.
 *
 * Explicit environment wins over the repo's default, so pointing a script at the emulator (or at a
 * second project) stays a matter of exporting one variable.
 */
export function resolveProjectId() {
  const fromEnv = process.env.GOOGLE_CLOUD_PROJECT ?? process.env.GCLOUD_PROJECT;
  if (fromEnv) return fromEnv.trim();

  try {
    const rc = JSON.parse(readFileSync(FIREBASERC, "utf8"));
    return typeof rc?.projects?.default === "string" ? rc.projects.default : "";
  } catch {
    // Absent or unreadable .firebaserc is not an error here — the caller reports the whole failure
    // once, with the instructions for fixing it.
    return "";
  }
}

/**
 * Resolves the project and publishes it to the Admin SDK, returning the id.
 *
 * Must be called **before** importing `lib/config/firebaseAdmin.js`, which calls `initializeApp()`
 * at module load and reads the environment then — hence the dynamic import in each script.
 */
export function requireProjectId() {
  const projectId = resolveProjectId();
  if (!projectId) {
    console.error(
      "No project id resolved. Set GOOGLE_CLOUD_PROJECT (or GOOGLE_APPLICATION_CREDENTIALS to a " +
        "service-account key), or run from a checkout with backend/firebase/.firebaserc present.",
    );
    process.exit(1);
  }
  process.env.GOOGLE_CLOUD_PROJECT = projectId;
  return projectId;
}
