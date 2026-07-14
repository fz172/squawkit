import { onSchedule } from "firebase-functions/v2/scheduler";

import {
  FUNCTION_REGION,
  ORPHAN_BLOB_GRACE_DAYS,
  STORAGE_SWEEP_DRY_RUN,
  STORAGE_SWEEP_SCHEDULE,
  TOMBSTONE_RETENTION_DAYS,
} from "../config/env.js";
import { runStorageSweep } from "./storageSweep.js";

/**
 * The sweep on a timer (#159).
 *
 * `onSchedule` provisions a **Cloud Scheduler** job, which is also how you run it **on demand**:
 * the job has a "Force run" button in the GCP console. Scheduler invokes it as a trusted caller, so
 * there is no endpoint to expose and no admin auth to design — and therefore no callable that can
 * delete every user's data sitting on the internet waiting to be found.
 *
 * Everything is configurable from `.env` — schedule, retention, grace window, dry-run — so arming it
 * costs a redeploy. That is the point: switching off [STORAGE_SWEEP_DRY_RUN] makes an irreversible
 * job irreversible for real, and it should not be reachable by anything as casual as a wrong argument
 * in an RPC.
 *
 * What it did, and to what, is in the logs — see [runStorageSweep].
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
