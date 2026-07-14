import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";

import {
  FUNCTION_REGION,
  ORPHAN_BLOB_GRACE_DAYS,
  STORAGE_SWEEP_ADMIN_UIDS,
  STORAGE_SWEEP_DRY_RUN,
  STORAGE_SWEEP_SCHEDULE,
  TOMBSTONE_RETENTION_DAYS,
} from "../config/env.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import { runStorageSweep, type SweepReport } from "./storageSweep.js";

/**
 * The sweep on a timer (#159).
 *
 * `onSchedule` provisions a **Cloud Scheduler** job, which is also how you run it **on demand**:
 * the job has a "Force run" button in the GCP console. No extra endpoint, no auth to design.
 *
 * Everything is configurable from `.env` — schedule, retention, grace window, dry-run — so changing
 * any of it is a redeploy, not a code edit.
 */
export const scheduledStorageSweep = onSchedule(
  { schedule: STORAGE_SWEEP_SCHEDULE, region: FUNCTION_REGION, timeoutSeconds: 540 },
  async () => {
    await runStorageSweep({
      dryRun: STORAGE_SWEEP_DRY_RUN,
      tombstoneRetentionDays: TOMBSTONE_RETENTION_DAYS,
      orphanGraceDays: ORPHAN_BLOB_GRACE_DAYS,
    });
  },
);

type SweepRequest = {
  /** Defaults to the deployed setting, which is dry-run. Pass `false` to actually delete. */
  dryRun?: boolean;
  tombstoneRetentionDays?: number;
  orphanGraceDays?: number;
  /** Rehearse on one account before letting it loose on everyone. */
  onlyUid?: string;
};

/**
 * The sweep on demand, for a script or a one-off (#159).
 *
 * Scheduler's "Force run" covers the common case; this exists for the rest: overriding the settings
 * without redeploying, rehearsing against a single account, and reading the report back rather than
 * digging it out of the logs.
 *
 * **Admin-only.** The caller's uid must be in `STORAGE_SWEEP_ADMIN_UIDS`. An empty allowlist denies
 * everyone — a function that deletes every user's data must fail closed if nobody remembered to
 * configure who may call it.
 */
export const runStorageSweepOnDemand = onCall<SweepRequest, Promise<SweepReport>>(
  { region: FUNCTION_REGION, enforceAppCheck: true, timeoutSeconds: 540 },
  async (request): Promise<SweepReport> => {
    const { uid } = requireAuthenticatedApp(request);
    if (!STORAGE_SWEEP_ADMIN_UIDS.includes(uid)) {
      throw new HttpsError("permission-denied", "Not an administrator.");
    }

    const data = request.data ?? {};
    return runStorageSweep({
      // The deployed default wins unless the caller says otherwise, and the deployed default is
      // dry-run. Arming this must be a deliberate act, not an omission.
      dryRun: typeof data.dryRun === "boolean" ? data.dryRun : STORAGE_SWEEP_DRY_RUN,
      tombstoneRetentionDays: positive(data.tombstoneRetentionDays, TOMBSTONE_RETENTION_DAYS),
      orphanGraceDays: positive(data.orphanGraceDays, ORPHAN_BLOB_GRACE_DAYS),
      onlyUid: typeof data.onlyUid === "string" && data.onlyUid.length > 0 ? data.onlyUid : undefined,
    });
  },
);

/** A caller cannot shrink a safety window to zero by passing junk. */
function positive(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0
    ? Math.floor(value)
    : fallback;
}
