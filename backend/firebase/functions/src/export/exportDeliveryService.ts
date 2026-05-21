import { HttpsError } from "firebase-functions/v2/https";

import {
  EXPORT_DELIVERY_LEASE_TTL_MS,
  EXPORT_DELIVERY_SIGNED_URL_TTL_MS,
} from "../config/env.js";
import {
  EXPORT_DELIVERY_STATE,
  type DeliveryDispatchResult,
} from "./exportModels.js";
import { ExportMailer } from "./exportMailer.js";
import { ExportManifestRepository } from "./exportManifestRepository.js";
import { ExportStorage } from "./exportStorage.js";

export class ExportDeliveryService {
  constructor(
    private readonly manifestRepository: ExportManifestRepository = new ExportManifestRepository(),
    private readonly storage: ExportStorage = new ExportStorage(),
    private readonly mailer: ExportMailer = new ExportMailer(),
  ) {}

  async requestDelivery(uid: string, exportId: string): Promise<DeliveryDispatchResult> {
    const nowEpochMillis = Date.now();
    const lease = await this.manifestRepository.beginDelivery(
      uid,
      exportId,
      nowEpochMillis,
      nowEpochMillis + EXPORT_DELIVERY_LEASE_TTL_MS,
    );
    if (lease.kind !== "proceed") {
      return lease.result;
    }

    const manifest = lease.manifest;
    try {
      const destinationEmail = manifest.destinationEmail?.trim() ?? "";
      if (destinationEmail.length === 0) {
        throw new HttpsError("failed-precondition", "Export delivery email is not configured.");
      }
      const remoteArchiveRef = manifest.remoteArchiveRef?.trim() ?? "";
      if (remoteArchiveRef.length === 0) {
        throw new HttpsError("failed-precondition", "Export archive is not uploaded yet.");
      }
      const signedUrl = await this.storage.createSignedDownloadUrl(
        remoteArchiveRef,
        nowEpochMillis + EXPORT_DELIVERY_SIGNED_URL_TTL_MS,
      );
      await this.mailer.send({
        destinationEmail,
        fileName: manifest.fileName,
        downloadUrl: signedUrl,
      });
      const sentAtEpochMillis = Date.now();
      await this.manifestRepository.markSent(uid, exportId, sentAtEpochMillis);
      return {
        status: "sent",
        deliveryState: EXPORT_DELIVERY_STATE.SENT,
        deliverySentAtEpochMillis: sentAtEpochMillis,
      };
    } catch (error) {
      const failureCode = error instanceof HttpsError ? error.code : "mail-send-failed";
      const failureMessage = error instanceof Error ? error.message : "Export delivery failed.";
      await this.manifestRepository.markFailed(uid, exportId, failureCode, failureMessage);
      return {
        status: "failed",
        deliveryState: EXPORT_DELIVERY_STATE.FAILED,
        deliveryFailureCode: failureCode,
        deliveryFailureMessage: failureMessage,
      };
    }
  }
}
