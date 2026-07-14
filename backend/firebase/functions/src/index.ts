import { onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "./config/env.js";
import { requestExportDelivery } from "./export/requestExportDelivery.js";
import { cancelAircraftShareInvite } from "./sharing/cancelAircraftShareInvite.js";
import { createAircraftShareInvite } from "./sharing/createAircraftShareInvite.js";
import { onAircraftDeleted } from "./sharing/onAircraftDeleted.js";
import { previewAircraftShareInvite } from "./sharing/previewAircraftShareInvite.js";
import { onRecordDeleted } from "./storage/onRecordDeleted.js";
import { scheduledStorageSweep } from "./storage/storageSweepTriggers.js";
import { redeemAircraftShareInvite } from "./sharing/redeemAircraftShareInvite.js";
import { revokeAircraftShare } from "./sharing/revokeAircraftShare.js";
import { updateAircraftShareRole } from "./sharing/updateAircraftShareRole.js";
import { requireAuthenticatedApp } from "./shared/auth.js";

type HealthProbeResponse = {
  status: "ok";
  message: string;
  uid: string;
  appId: string;
};

export const health_probe = onCall<unknown, HealthProbeResponse>(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true,
  },
  (request) => {
    const { uid, appId } = requireAuthenticatedApp(request);

    return {
      status: "ok",
      message: "health probe passed",
      uid,
      appId,
    };
  },
);

export { requestExportDelivery };
export { redeemAircraftShareInvite };
export { revokeAircraftShare };
export { updateAircraftShareRole };
export { onAircraftDeleted };
export { createAircraftShareInvite, previewAircraftShareInvite, cancelAircraftShareInvite };
export { onRecordDeleted };
export { scheduledStorageSweep };
