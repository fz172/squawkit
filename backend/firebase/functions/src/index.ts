import { onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "./config/env.js";
import { deleteMyAccount } from "./account/deleteMyAccount.js";
import { requestExportDelivery } from "./export/requestExportDelivery.js";
import { cancelThingShareInvite } from "./sharing/cancelThingShareInvite.js";
import { createThingShareInvite } from "./sharing/createThingShareInvite.js";
import {
  onNotifiableThingRecordWritten,
  onNotifiableThingWritten,
} from "./notifications/onRecordWritten.js";
import { onThingDeleted } from "./sharing/onThingDeleted.js";
import { previewThingShareInvite } from "./sharing/previewThingShareInvite.js";
import { getBlobUploadSession } from "./storage/getBlobUploadSession.js";
import { onThingRecordDeleted } from "./storage/onRecordDeleted.js";
import { streamBlob } from "./storage/streamBlob.js";
import { scheduledStorageSweep } from "./storage/storageSweepTriggers.js";
import { redeemThingShareInvite } from "./sharing/redeemThingShareInvite.js";
import { revokeThingShare } from "./sharing/revokeThingShare.js";
import { updateThingShareRole } from "./sharing/updateThingShareRole.js";
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
export { redeemThingShareInvite };
export { revokeThingShare };
export { updateThingShareRole };
// MIGRATION (task F3): the `aircraft`-path twins are gone. Phase F2 deleted the documents they
// watched, so they could only ever have fired on a tree that no longer exists. Removing an export
// deletes the deployed function — intended here, and the reason F3 waits until F2 has run.
export { onThingDeleted };
export { createThingShareInvite, previewThingShareInvite, cancelThingShareInvite };
export { onThingRecordDeleted };
export { onNotifiableThingRecordWritten, onNotifiableThingWritten };
export { scheduledStorageSweep };
export { streamBlob };
export { getBlobUploadSession };
export { grantPromoEntitlement };
export { projectAttachmentEntitlement };
export { revenueCatWebhook };
export { reconcileMyEntitlement };
export { scheduledEntitlementReconcile };
