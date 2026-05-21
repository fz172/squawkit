import { onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "./config/env.js";
import { requestExportDelivery } from "./export/requestExportDelivery.js";
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
