import { logger } from "firebase-functions/v2";

import { adminDb } from "../config/firebaseAdmin.js";
import { applyEntitlement } from "./applyEntitlement.js";
import {
  ENTITLEMENT_SOURCE,
  SUBSCRIPTION_LIFECYCLE,
  SUBSCRIPTION_STATUS,
} from "./entitlementModel.js";
import {
  fetchSubscriber,
  RevenueCatRateLimitError,
  type RevenueCatSubscriber,
} from "./revenueCatApi.js";
import { normalizeSubscriber } from "./revenueCatSubscriberSource.js";

/**
 * The entitlement reconciler (#355) — the backstop for drift between `subscriptions/{uid}` and
 * RevenueCat's actual state.
 *
 * Webhooks remain the primary path; this exists because a webhook that never arrives is silent.
 * There is no error, no retry and no alarm — the stale value simply sits there looking
 * authoritative. Causes seen in practice: an event we ignored (an entitlement id that stopped
 * matching), a delivery failure past RevenueCat's retry budget, our own 500s, a provider outage.
 *
 * ## What it looks at, and what it deliberately doesn't
 *
 * Only accounts the local doc currently grants Pro to, whose period end is comfortably past. That
 * is the population that can be wrong in the direction that matters: still entitled here, no longer
 * paying there. It cannot find the opposite case — someone who paid and never got Pro — because
 * such an account has no Pro doc to notice. That case is caught at purchase time by the client,
 * where the person is actually waiting; a daily job would be the wrong instrument for it anyway.
 *
 * ## Why a client-triggered ping is not enough on its own
 *
 * The worst drift is the account that stops opening the app. It never triggers anything — and it is
 * not victimless, because a host's entitlement projects `attachmentsEnabled` onto shares that *other*
 * people read. A lapsed host who walks away would otherwise keep attachments enabled for their
 * members indefinitely.
 */

export type ReconcileOptions = {
  apiKey: string;
  nowMillis: number;
  /** How far past its period end an entitlement must be before it is questioned. */
  graceMs: number;
  maxPerRun: number;
  requestSpacingMs: number;
  /** Seam for tests; production uses the real client. */
  fetchSubscriberImpl?: (
    appUserId: string,
    apiKey: string,
  ) => Promise<RevenueCatSubscriber | null>;
  /** Seam for tests, so pacing does not make the suite sleep. */
  sleepImpl?: (ms: number) => Promise<void>;
};

export type ReconcileSummary = {
  /** Accounts whose local doc looked stale and were checked against RevenueCat. */
  examined: number;
  /** Accounts whose entitlement was corrected. */
  corrected: number;
  /** Accounts RevenueCat agreed with — the expected outcome for almost all of them. */
  unchanged: number;
  /** Accounts skipped because the lookup failed; retried on the next run. */
  failed: number;
  /** True when more accounts were stale than the run was allowed to process. */
  backlogExceeded: boolean;
  /** Of [examined], how many were selected only to learn a management URL (#363). */
  selectedForManagementUrl: number;
  /** Accounts that came out of this run holding a non-empty management URL. */
  managementUrlResolved: number;
  /** Accounts RevenueCat reported no management URL for; they settle on "" and stop being asked. */
  managementUrlAbsent: number;
};

/** Why an account was picked up, carried through to the logs so a run explains itself. */
type SelectionReason = "stale" | "missing_management_url" | "stale_and_missing_management_url";

/** Lifecycles that still grant access, and can therefore be wrong in the expensive direction. */
const ENTITLING_LIFECYCLES = new Set<number>([
  SUBSCRIPTION_LIFECYCLE.ACTIVE,
  SUBSCRIPTION_LIFECYCLE.TRIALING,
  SUBSCRIPTION_LIFECYCLE.GRACE,
  SUBSCRIPTION_LIFECYCLE.CANCELED,
]);

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export async function runEntitlementReconcile(
  options: ReconcileOptions,
): Promise<ReconcileSummary> {
  const {
    apiKey,
    nowMillis,
    graceMs,
    maxPerRun,
    requestSpacingMs,
    fetchSubscriberImpl = (uid, key) => fetchSubscriber(uid, key),
    sleepImpl = sleep,
  } = options;

  const cutoff = nowMillis - graceMs;
  const stale = await findStaleEntitlements(cutoff);

  const summary: ReconcileSummary = {
    examined: 0,
    corrected: 0,
    unchanged: 0,
    failed: 0,
    backlogExceeded: stale.length > maxPerRun,
    selectedForManagementUrl: 0,
    managementUrlResolved: 0,
    managementUrlAbsent: 0,
  };

  // The selection itself, before any provider call. Without this a run that examines nothing is
  // indistinguishable from a run that never queried Firestore, which is the first thing you want to
  // rule out when an expected account is not picked up.
  logger.info("Entitlement reconcile starting", {
    candidates: stale.length,
    stale: stale.filter((c) => c.reason === "stale").length,
    missingManagementUrl: stale.filter((c) => c.reason === "missing_management_url").length,
    staleAndMissingManagementUrl: stale.filter(
      (c) => c.reason === "stale_and_missing_management_url",
    ).length,
    maxPerRun,
    cutoffMillis: cutoff,
  });

  for (const [index, candidate] of stale.slice(0, maxPerRun).entries()) {
    if (index > 0) await sleepImpl(requestSpacingMs);
    summary.examined++;
    if (candidate.reason !== "stale") summary.selectedForManagementUrl++;
    try {
      const subscriber = await fetchSubscriberImpl(candidate.uid, apiKey);
      if (subscriber == null) {
        // RevenueCat has never seen this customer, yet we are granting them Pro. Almost certainly a
        // uid that was never aliased to the store identity. Surfaced rather than revoked: the safe
        // failure is a free month, not locking a paying pilot out of their logbook on the strength
        // of a lookup that may simply have asked about the wrong id.
        logger.error("Reconcile found no RevenueCat customer for an entitled account", {
          uid: candidate.uid,
        });
        summary.failed++;
        continue;
      }

      const resolved = normalizeSubscriber(candidate.uid, subscriber, nowMillis);
      const hasManagementUrl = (resolved.managementUrl ?? "").length > 0;
      if (hasManagementUrl) summary.managementUrlResolved++;
      else summary.managementUrlAbsent++;

      const { applied } = await applyEntitlement(resolved);
      if (applied) {
        summary.corrected++;
        logger.warn("Reconciled a drifted entitlement", {
          uid: candidate.uid,
          reason: candidate.reason,
          from: { status: candidate.status, lifecycle: candidate.lifecycle },
          to: { status: resolved.status, lifecycle: resolved.lifecycle },
          currentPeriodEndMillis: resolved.currentPeriodEndMillis,
          managementUrl: resolved.managementUrl ?? "",
          eventId: resolved.eventId,
        });
      } else {
        // The deterministic reconcile id already applied, so RevenueCat agrees with what we hold.
        summary.unchanged++;
        // Logged rather than silently counted: an account selected for a management URL that comes
        // out "unchanged" means the write was deduped, which is the failure mode that would leave it
        // re-selected on every run. Seeing the event id here is what distinguishes that from a
        // genuinely healthy no-op.
        logger.info("Reconcile left an entitlement unchanged", {
          uid: candidate.uid,
          reason: candidate.reason,
          managementUrl: resolved.managementUrl ?? "",
          eventId: resolved.eventId,
        });
      }
    } catch (error) {
      if (error instanceof RevenueCatRateLimitError) {
        // Stop the run rather than push through the limit; tomorrow picks up where this left off,
        // and nothing here is urgent enough to justify hammering a rate-limited API.
        logger.warn("Reconcile hit the RevenueCat rate limit; ending the run early", {
          retryAfterSeconds: error.retryAfterSeconds,
          examined: summary.examined,
        });
        summary.failed++;
        break;
      }
      logger.error("Reconcile failed for an account", { uid: candidate.uid, error });
      summary.failed++;
    }
  }

  if (summary.backlogExceeded) {
    // The alarm, not a throughput problem: a healthy pipeline drifts approximately zero accounts,
    // so a full run means webhooks are broken and the fix is upstream.
    logger.error("More entitlements are stale than one reconcile run may process", {
      stale: stale.length,
      maxPerRun,
    });
  }
  logger.info("Entitlement reconcile complete", summary);
  return summary;
}

type StaleCandidate = {
  uid: string;
  status: number;
  lifecycle: number;
  reason: SelectionReason;
};

/**
 * Accounts worth re-asking RevenueCat about: those whose period end is well past, plus those still
 * missing a management URL.
 *
 * Queried on `status == PRO` alone — a single-field equality, served by Firestore's automatic index
 * — then filtered in memory. Filtering server-side on lifecycle and period end too would need a
 * composite index, and this project manages no indexes as code; the read set is bounded by the
 * number of *paying subscribers*, which is the right thing to be proportional to. Revisit with a
 * declared index if that count ever makes a daily pass expensive.
 *
 * ## The management-URL backfill (#363)
 *
 * `management_url` exists only on the REST subscriber view, so it can only ever be learned by a
 * reconcile — and the staleness rule above never looks at a *healthy* subscription, whose period end
 * is by definition in the future. Without this second population a subscriber who paid and whose
 * webhook worked would never be reconciled at all, and web would never get a Manage link. That is
 * the happy path, so it has to be covered here rather than left to the drift backstop.
 *
 * Deliberately keyed on the field being **absent**, not falsy. Once a reconcile has written it —
 * including writing `""` because the provider reports none, which is what the Test Store always does
 * — the account stops matching. Testing for falsy instead would re-query those accounts every single
 * day forever, and the deterministic reconcile event id means the write that would clear them is
 * skipped as a duplicate, so they could never age out.
 */
async function findStaleEntitlements(cutoffMillis: number): Promise<StaleCandidate[]> {
  const snapshot = await adminDb
    .collection("subscriptions")
    .where("status", "==", SUBSCRIPTION_STATUS.PRO)
    .get();

  const stale: StaleCandidate[] = [];
  for (const doc of snapshot.docs) {
    const data = doc.data() ?? {};
    const lifecycle = typeof data.lifecycle === "number" ? data.lifecycle : 0;
    const periodEnd =
      typeof data.currentPeriodEndMillis === "number" ? data.currentPeriodEndMillis : 0;
    if (!ENTITLING_LIFECYCLES.has(lifecycle)) continue;

    // A zero period end means "never known" rather than "expired at the epoch"; leave those alone
    // instead of interrogating RevenueCat about every comped account on every run.
    const isStale = periodEnd > 0 && periodEnd < cutoffMillis;
    // Store purchases only: a server grant has no store, so RevenueCat may not know the customer at
    // all and the lookup would log the "entitled account RevenueCat has never seen" alarm on every
    // run. There is also nothing for a comped pilot to manage.
    const needsManagementUrl =
      data.managementUrl === undefined && data.source === ENTITLEMENT_SOURCE.STORE_PURCHASE;

    if (!isStale && !needsManagementUrl) continue;
    stale.push({
      uid: doc.id,
      status: SUBSCRIPTION_STATUS.PRO,
      lifecycle,
      reason: isStale
        ? needsManagementUrl
          ? "stale_and_missing_management_url"
          : "stale"
        : "missing_management_url",
    });
  }
  return stale;
}
