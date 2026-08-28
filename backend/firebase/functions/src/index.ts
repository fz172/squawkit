import { onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "./config/env.js";
import { deleteMyAccount } from "./account/deleteMyAccount.js";
import { requestExportDelivery } from "./export/requestExportDelivery.js";
import { cancelAircraftShareInvite } from "./sharing/cancelAircraftShareInvite.js";
import { createAircraftShareInvite } from "./sharing/createAircraftShareInvite.js";
import {
  onNotifiableAircraftWritten,
  onNotifiableRecordWritten,
} from "./notifications/onRecordWritten.js";
import { onAircraftDeleted } from "./sharing/onAircraftDeleted.js";
import { previewAircraftShareInvite } from "./sharing/previewAircraftShareInvite.js";
import { getBlobUploadSession } from "./storage/getBlobUploadSession.js";
import { onRecordDeleted } from "./storage/onRecordDeleted.js";
import { streamBlob } from "./storage/streamBlob.js";
import { scheduledStorageSweep } from "./storage/storageSweepTriggers.js";
import { redeemAircraftShareInvite } from "./sharing/redeemAircraftShareInvite.js";
import { revokeAircraftShare } from "./sharing/revokeAircraftShare.js";
import { updateAircraftShareRole } from "./sharing/updateAircraftShareRole.js";
import { grantPromoEntitlement } from "./subscription/grantPromoEntitlement.js";
import { projectAttachmentEntitlement } from "./subscription/projectAttachmentEntitlement.js";
import { scheduledEntitlementReconcile } from "./subscription/entitlementReconcileTriggers.js";
import { reconcileMyEntitlement } from "./subscription/reconcileMyEntitlement.js";
import { revenueCatWebhook } from "./subscription/revenueCatWebhook.js";
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
export { deleteMyAccount };
export { redeemAircraftShareInvite };
export { revokeAircraftShare };
export { updateAircraftShareRole };
// MIGRATION (thing_migration_design.md §2.7, §2.7c / task B9): the four `Thing`-path triggers are
// written but NOT exported here — they ship with C2, after the Phase D copy is done. A cutover copy
// creates each document, so every copied record would look like a new write to them. Phase F3
// removes the `aircraft` half once no account is left on it.
export { onAircraftDeleted };
export { createAircraftShareInvite, previewAircraftShareInvite, cancelAircraftShareInvite };
export { onRecordDeleted };
export { onNotifiableRecordWritten, onNotifiableAircraftWritten };
export { scheduledStorageSweep };
export { streamBlob };
export { getBlobUploadSession };
export { grantPromoEntitlement };
export { projectAttachmentEntitlement };
export { revenueCatWebhook };
export { reconcileMyEntitlement };
export { scheduledEntitlementReconcile };
