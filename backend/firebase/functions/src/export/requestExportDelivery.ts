import { HttpsError, onCall } from "firebase-functions/v2/https";

import { FUNCTION_REGION } from "../config/env.js";
import { ExportDeliveryService } from "./exportDeliveryService.js";
import { type DeliveryDispatchResult } from "./exportModels.js";
import { requireAuthenticatedApp } from "../shared/auth.js";

export type RequestExportDeliveryRequest = {
  exportId: string;
};

export type RequestExportDeliveryResponse = {
  status: DeliveryDispatchResult["status"];
  exportId: string;
  uid: string;
  appId: string;
  deliveryState: DeliveryDispatchResult["deliveryState"];
  deliverySentAtEpochMillis: number;
  deliveryFailureCode: string;
  deliveryFailureMessage: string;
};

const deliveryService = new ExportDeliveryService();

export const requestExportDelivery = onCall<
  RequestExportDeliveryRequest,
  Promise<RequestExportDeliveryResponse>
>(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true,
  },
  async request => {
    const { uid, appId } = requireAuthenticatedApp(request);
    const { exportId } = parseRequest(request.data);
    const result = await deliveryService.requestDelivery(uid, exportId);

    return {
      status: result.status,
      exportId,
      uid,
      appId,
      deliveryState: result.deliveryState,
      deliverySentAtEpochMillis: result.deliverySentAtEpochMillis ?? 0,
      deliveryFailureCode: result.deliveryFailureCode ?? "",
      deliveryFailureMessage: result.deliveryFailureMessage ?? "",
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
