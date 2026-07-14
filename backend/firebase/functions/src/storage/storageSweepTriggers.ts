import { onSchedule } from "firebase-functions/v2/scheduler";

import {
  FUNCTION_REGION,
  STORAGE_SWEEP_SCHEDULE,
  orphanBlobGraceDays,
  storageSweepDryRun,
  tombstoneRetentionDays,
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
 * Retention, grace window and dry-run come from `.env`, required and unvalidated by no default: a
 * config the sweep cannot read aborts the run rather than falling back to numbers nobody chose. The
 * schedule is a code constant — the deploy-time analysis cannot see .env (see config/env.ts).
 *
 * Arming it therefore costs a redeploy, which is the point: making an irreversible job irreversible
 * for real should not be reachable by anything as casual as a wrong argument in an RPC.
 *
 * What it did, and to what, is in the logs — see [runStorageSweep].
 */
export const scheduledStorageSweep = onSchedule(
  { schedule: STORAGE_SWEEP_SCHEDULE, region: FUNCTION_REGION, timeoutSeconds: 540 },
  async () => {
    // Read per run, not at module load: a config the sweep cannot read must abort the run rather
    // than fall back to numbers nobody chose. Throwing here deletes nothing and shows up in the logs.
    await runStorageSweep({
      dryRun: storageSweepDryRun(),
      tombstoneRetentionDays: tombstoneRetentionDays(),
      orphanGraceDays: orphanBlobGraceDays(),
    });
  },
);
