export const FUNCTION_REGION = "us-central1";
export const FUNCTIONS_RUNTIME_NODE = "22";

export const EXPORT_DELIVERY_PROVIDER = process.env.EXPORT_DELIVERY_PROVIDER ?? "resend";
export const EXPORT_DELIVERY_SIGNED_URL_TTL_MS = 24 * 60 * 60 * 1000;
export const EXPORT_DELIVERY_LEASE_TTL_MS = 10 * 60 * 1000;

export type ExportDeliveryConfig = {
  provider: string;
  fromEmail: string;
  apiKey: string;
};

export function requireExportDeliveryConfig(): ExportDeliveryConfig {
  return {
    provider: EXPORT_DELIVERY_PROVIDER,
    fromEmail: requiredEnv("EXPORT_DELIVERY_FROM_EMAIL"),
    apiKey: requiredEnv("EXPORT_DELIVERY_API_KEY"),
  };
}

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (value == null || value.length === 0) {
    throw new Error(`Missing required environment variable ${name}.`);
  }
  return value;
}
