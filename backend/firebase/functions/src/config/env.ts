import { defineSecret } from "firebase-functions/params";

export const FUNCTION_REGION = "us-central1";
export const FUNCTIONS_RUNTIME_NODE = "22";
export const EXPORT_DELIVERY_API_KEY = defineSecret("EXPORT_DELIVERY_API_KEY");

export const EXPORT_DELIVERY_PROVIDER = process.env.EXPORT_DELIVERY_PROVIDER ?? "resend";
export const EXPORT_DELIVERY_SIGNED_URL_TTL_MS = 24 * 60 * 60 * 1000;
export const EXPORT_DELIVERY_LEASE_TTL_MS = 10 * 60 * 1000;

// Minimum gap before the same already-delivered export can be re-sent. Configured in seconds via
// .env (EXPORT_DELIVERY_RESEND_COOLDOWN_SECONDS); defaults to 24 hours.
export const EXPORT_DELIVERY_RESEND_COOLDOWN_SECONDS = readNonNegativeIntEnv(
  "EXPORT_DELIVERY_RESEND_COOLDOWN_SECONDS",
  24 * 60 * 60,
);
export const EXPORT_DELIVERY_RESEND_COOLDOWN_MS =
  EXPORT_DELIVERY_RESEND_COOLDOWN_SECONDS * 1000;

// --- Storage sweep (#159) ---------------------------------------------------------------------
//
// Every value below is REQUIRED, with no code-side default. A silent fallback would let the deployed
// sweep run with a retention window nobody chose — .env would say one thing and the function would do
// another, and the first sign of the mismatch would be deleted data. A config this function cannot
// read is a config it must not act on: these throw, the deploy fails loudly, and nothing is deleted.

/** How often the sweep runs. Any App Engine cron or unix-cron expression. Read at deploy time. */
export const STORAGE_SWEEP_SCHEDULE = requiredEnv("STORAGE_SWEEP_SCHEDULE");

/**
 * How long a tombstone survives before it is hard-deleted.
 *
 * This is a CORRECTNESS constraint, not a cost knob. Purge a tombstone before every device has seen
 * it and the record COMES BACK: an offline client still holding the live row syncs, finds no
 * tombstone, and pushes it up again as a fresh write. The window must comfortably exceed the longest
 * plausible offline stretch — a pilot who flies a season without opening the app is not unusual.
 *
 * Keep it in step with the client's local TombstoneGc so the two ends agree.
 */
export const TOMBSTONE_RETENTION_DAYS = requiredNonNegativeIntEnv("TOMBSTONE_RETENTION_DAYS");

/**
 * How old an unreferenced blob must be before the sweep will collect it.
 *
 * Records and blobs travel in SEPARATE queues, so a freshly uploaded blob can land before the record
 * that references it. Without this grace window the sweep would delete a photo the user just took,
 * moments before its log arrives.
 *
 * It never applies to a blob a live record references — those are not candidates at any age.
 */
export const ORPHAN_BLOB_GRACE_DAYS = requiredNonNegativeIntEnv("ORPHAN_BLOB_GRACE_DAYS");

/**
 * Dry run: report what WOULD be deleted, delete nothing.
 *
 * The failure mode of this function is destroying a user's photos and resurrecting their deleted
 * records, so arming it must be an explicit written act — "false", spelled out in .env. Anything
 * else, including a missing value, is rejected rather than guessed in either direction.
 */
export const STORAGE_SWEEP_DRY_RUN = requiredBooleanEnv("STORAGE_SWEEP_DRY_RUN");

export type ExportDeliveryConfig = {
  provider: string;
  fromEmail: string;
  apiKey: string;
};

export function requireExportDeliveryConfig(): ExportDeliveryConfig {
  return {
    provider: EXPORT_DELIVERY_PROVIDER,
    fromEmail: requiredEnv("EXPORT_DELIVERY_FROM_EMAIL"),
    apiKey: requiredSecretValue(EXPORT_DELIVERY_API_KEY.value(), "EXPORT_DELIVERY_API_KEY"),
  };
}

function readNonNegativeIntEnv(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  if (raw == null || raw.length === 0) return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : fallback;
}

/** A non-negative integer that MUST be present and MUST parse. Junk is an error, not a fallback. */
function requiredNonNegativeIntEnv(name: string): number {
  const raw = requiredEnv(name);
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`Environment variable ${name} must be a non-negative number; got "${raw}".`);
  }
  return Math.floor(parsed);
}

/** Strictly "true" or "false". A typo must not quietly arm a destructive job — or quietly disarm it. */
function requiredBooleanEnv(name: string): boolean {
  const raw = requiredEnv(name);
  if (raw !== "true" && raw !== "false") {
    throw new Error(`Environment variable ${name} must be "true" or "false"; got "${raw}".`);
  }
  return raw === "true";
}

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (value == null || value.length === 0) {
    throw new Error(`Missing required environment variable ${name}.`);
  }
  return value;
}

function requiredSecretValue(value: string | undefined, name: string): string {
  const trimmed = value?.trim();
  if (trimmed == null || trimmed.length === 0) {
    throw new Error(`Missing required secret ${name}.`);
  }
  return trimmed;
}
