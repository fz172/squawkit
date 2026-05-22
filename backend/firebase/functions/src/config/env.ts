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
