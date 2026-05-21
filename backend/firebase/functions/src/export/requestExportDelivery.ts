import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import {
  EXPORT_DELIVERY_STATE,
  type ExportDeliveryState,
} from "./exportModels.js";
import { requireAuthenticatedApp } from "../shared/auth.js";

export type RequestExportDeliveryRequest = {
  exportId: string;
};

export type RequestExportDeliveryResponse = {
  status: "accepted";
  exportId: string;
  uid: string;
  appId: string;
  deliveryState: ExportDeliveryState;
  message: string;
};

export const requestExportDelivery = onCall<
  RequestExportDeliveryRequest,
  RequestExportDeliveryResponse
>(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true,
  },
  (request) => {
    const { uid, appId } = requireAuthenticatedApp(request);
    const { exportId } = parseRequest(request.data);

    return {
      status: "accepted",
      exportId,
      uid,
      appId,
      deliveryState: EXPORT_DELIVERY_STATE.NOT_IMPLEMENTED,
      message:
        "requestExportDelivery is scaffolded for Milestone 0 but delivery is not implemented yet.",
    };
  },
);

function parseRequest(data: unknown): RequestExportDeliveryRequest {
  const exportId = typeof (data as { exportId?: unknown } | null)?.exportId === "string" ?
    (data as { exportId: string }).exportId.trim() :
    "";

  if (exportId.length === 0) {
    throw new HttpsError(
      "invalid-argument",
      "requestExportDelivery requires a non-empty exportId.",
    );
  }

  return { exportId };
}
