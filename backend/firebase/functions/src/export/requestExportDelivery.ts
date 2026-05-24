import { HttpsError, onCall } from "firebase-functions/v2/https";

import {
  EXPORT_DELIVERY_API_KEY,
  FUNCTION_REGION,
} from "../config/env.js";
import { ExportDeliveryService } from "./exportDeliveryService.js";
import { type DeliveryDispatchResult } from "./exportModels.js";
import { requireAuthenticatedApp } from "../shared/auth.js";
import {
  RequestExportDeliveryRequest,
  RequestExportDeliveryResponse,
} from "../generated/proto/rpc/request_export_delivery/request_export_delivery.js";

const deliveryService = new ExportDeliveryService();

export const requestExportDelivery = onCall<
  RequestExportDeliveryRequest,
  Promise<RequestExportDeliveryResponse>
>(
  {
    region: FUNCTION_REGION,
    enforceAppCheck: true,
    secrets: [EXPORT_DELIVERY_API_KEY],
  },
  async request => {
    const { uid, appId } = requireAuthenticatedApp(request);
    const { exportId, forceResend } = parseRequest(request.data);
    const result = await deliveryService.requestDelivery(uid, exportId, forceResend);

    return RequestExportDeliveryResponse.create({
      requestOutcome: result.requestOutcome,
      exportId,
      uid,
      appId,
      persistedDeliveryState: result.persistedDeliveryState,
      deliverySentAtEpochMillis: result.deliverySentAtEpochMillis ?? 0,
      deliveryFailureCode: result.deliveryFailureCode ?? "",
      deliveryFailureMessage: result.deliveryFailureMessage ?? "",
    });
  },
);

function parseRequest(data: unknown): RequestExportDeliveryRequest {
  const parsed = RequestExportDeliveryRequest.fromJSON(data);
  const exportId = parsed.exportId.trim();

  if (exportId.length === 0) {
    throw new HttpsError(
      "invalid-argument",
      "requestExportDelivery requires a non-empty exportId.",
    );
  }

  return { exportId, forceResend: parsed.forceResend };
}
