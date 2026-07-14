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
// All configurable from .env without touching code. The schedule string is read at deploy time, so
// changing it re-provisions the Cloud Scheduler job.

/** How often the sweep runs. Any App Engine cron or unix-cron expression. */
export const STORAGE_SWEEP_SCHEDULE = process.env.STORAGE_SWEEP_SCHEDULE ?? "every 24 hours";

/**
 * How long a tombstone survives before it is hard-deleted.
 *
 * This is a CORRECTNESS constraint, not a cost knob. Purge a tombstone before every device has seen
 * it and the record COMES BACK: an offline client still holding the live row syncs, finds no
 * tombstone, and pushes it up again as a fresh write. The window must comfortably exceed the longest
 * plausible offline stretch — a pilot who flies a season without opening the app is not unusual.
 *
 * 30 days, matching the client's local TombstoneGc so the two ends agree.
 */
export const TOMBSTONE_RETENTION_DAYS = readNonNegativeIntEnv("TOMBSTONE_RETENTION_DAYS", 30);

/**
 * How old an unreferenced blob must be before the sweep will collect it.
 *
 * Records and blobs travel in SEPARATE queues, so a freshly uploaded blob can land before the record
 * that references it. Without this grace window the sweep would delete a photo the user just took,
 * moments before its log arrives.
 */
export const ORPHAN_BLOB_GRACE_DAYS = readNonNegativeIntEnv("ORPHAN_BLOB_GRACE_DAYS", 7);

/**
 * Dry run: report what WOULD be deleted, delete nothing. Defaults to ON.
 *
 * The failure mode of this function is destroying a user's photos and resurrecting their deleted
 * records. It stays in rehearsal until the numbers it reports have been looked at by a human.
 */
export const STORAGE_SWEEP_DRY_RUN = process.env.STORAGE_SWEEP_DRY_RUN !== "false";

/** uids allowed to invoke the sweep on demand. Comma-separated in .env. */
export const STORAGE_SWEEP_ADMIN_UIDS = (process.env.STORAGE_SWEEP_ADMIN_UIDS ?? "")
  .split(",")
  .map((uid) => uid.trim())
  .filter((uid) => uid.length > 0);

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
