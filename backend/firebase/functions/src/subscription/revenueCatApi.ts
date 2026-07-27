/**
 * Minimal RevenueCat REST client — the *pull* half of the integration.
 *
 * Webhooks push what RevenueCat thinks changed; this asks what RevenueCat currently believes, which
 * is the only way to resolve drift (#355). Deliberately tiny: one endpoint, no SDK, no retries
 * beyond surfacing a rate limit to the caller so it can pace itself.
 */

/** A subscription entry under `subscriber.subscriptions[productId]`. Unread fields are ignored. */
export type RevenueCatSubscription = {
  expires_date?: string | null;
  purchase_date?: string | null;
  original_purchase_date?: string | null;
  grace_period_expires_date?: string | null;
  unsubscribe_detected_at?: string | null;
  billing_issues_detected_at?: string | null;
  refunded_at?: string | null;
  period_type?: string | null;
  store?: string | null;
};

/** An entitlement entry under `subscriber.entitlements[entitlementId]`. */
export type RevenueCatEntitlement = {
  expires_date?: string | null;
  purchase_date?: string | null;
  grace_period_expires_date?: string | null;
  product_identifier?: string | null;
};

export type RevenueCatSubscriber = {
  entitlements?: Record<string, RevenueCatEntitlement> | null;
  subscriptions?: Record<string, RevenueCatSubscription> | null;
  original_app_user_id?: string | null;
  first_seen?: string | null;
  management_url?: string | null;
};

/**
 * Rate limited by RevenueCat. Carries their `Retry-After` so the caller can back off by exactly as
 * long as asked rather than guessing.
 */
export class RevenueCatRateLimitError extends Error {
  constructor(public readonly retryAfterSeconds: number) {
    super(`RevenueCat rate limited; retry after ${retryAfterSeconds}s`);
    this.name = "RevenueCatRateLimitError";
  }
}

/** Any other non-success response. */
export class RevenueCatApiError extends Error {
  constructor(public readonly status: number) {
    super(`RevenueCat REST API returned ${status}`);
    this.name = "RevenueCatApiError";
  }
}

const BASE_URL = "https://api.revenuecat.com/v1";

/**
 * Fetches RevenueCat's view of one customer.
 *
 * Returns `null` for a customer RevenueCat has never seen (404) — a normal answer, not an error:
 * it means the account genuinely has no subscription, which is itself useful information when the
 * local doc claims otherwise.
 *
 * @throws {RevenueCatRateLimitError} on 429, carrying their Retry-After.
 * @throws {RevenueCatApiError} on any other failure, so a broken key or outage aborts the account
 *   rather than being mistaken for "no subscription" and silently revoking someone's Pro.
 */
export async function fetchSubscriber(
  appUserId: string,
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<RevenueCatSubscriber | null> {
  const response = await fetchImpl(`${BASE_URL}/subscribers/${encodeURIComponent(appUserId)}`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
    },
  });

  if (response.status === 404) return null;

  if (response.status === 429) {
    const header = response.headers?.get?.("Retry-After");
    const parsed = Number(header);
    throw new RevenueCatRateLimitError(Number.isFinite(parsed) && parsed > 0 ? parsed : 60);
  }

  if (!response.ok) throw new RevenueCatApiError(response.status);

  const body = (await response.json()) as { subscriber?: RevenueCatSubscriber } | null;
  // A 200 with no subscriber object is malformed; treat it as an error rather than as "no
  // subscription", because the difference decides whether someone keeps their Pro.
  if (body?.subscriber == null) throw new RevenueCatApiError(response.status);
  return body.subscriber;
}
