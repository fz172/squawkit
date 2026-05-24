import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

import { adminDb } from "../config/firebaseAdmin.js";
import {
  EXPORT_DELIVERY_STATE,
  type DeliveryDispatchResult,
  type ExportManifest,
} from "./exportModels.js";

type DeliveryLeaseResult =
  | { kind: "proceed"; manifest: ExportManifest }
  | { kind: "already-sent"; result: DeliveryDispatchResult }
  | { kind: "resend-throttled"; result: DeliveryDispatchResult }
  | { kind: "in-progress"; result: DeliveryDispatchResult };

export class ExportManifestRepository {
  async loadOwned(uid: string, exportId: string): Promise<ExportManifest> {
    const snapshot = await this.document(uid, exportId).get();
    if (!snapshot.exists) {
      throw new HttpsError("not-found", `Export ${exportId} does not exist.`);
    }
    return toManifest(snapshot.data(), exportId, uid);
  }

  async beginDelivery(
    uid: string,
    exportId: string,
    nowEpochMillis: number,
    leaseExpiresAtEpochMillis: number,
    forceResend: boolean,
    resendCooldownMs: number,
  ): Promise<DeliveryLeaseResult> {
    const ref = this.document(uid, exportId);
    return adminDb.runTransaction(async transaction => {
      const snapshot = await transaction.get(ref);
      if (!snapshot.exists) {
        throw new HttpsError("not-found", `Export ${exportId} does not exist.`);
      }
      const manifest = toManifest(snapshot.data(), exportId, uid);
      if (manifest.persistedDeliveryState === EXPORT_DELIVERY_STATE.SENT) {
        const sentAtEpochMillis = manifest.deliverySentAtEpochMillis ?? 0;
        // A plain (non-forced) request never re-sends an already-delivered export. A forced resend
        // re-sends only once the per-export cooldown has elapsed, otherwise it's throttled.
        const cooldownActive = sentAtEpochMillis + resendCooldownMs > nowEpochMillis;
        if (!forceResend || cooldownActive) {
          return {
            kind: forceResend ? "resend-throttled" : "already-sent",
            result: {
              requestOutcome: forceResend ? "resend-throttled" : "already-sent",
              persistedDeliveryState: EXPORT_DELIVERY_STATE.SENT,
              deliverySentAtEpochMillis: sentAtEpochMillis,
            },
          };
        }
        // Forced resend past the cooldown falls through to acquire a fresh delivery lease below.
      }
      const leaseExpiresAt = manifest.deliveryLeaseExpiresAtEpochMillis ?? 0;
      if (
        manifest.persistedDeliveryState === EXPORT_DELIVERY_STATE.SENDING &&
        leaseExpiresAt > nowEpochMillis
      ) {
        return {
          kind: "in-progress",
          result: {
            requestOutcome: "in-progress",
            persistedDeliveryState: EXPORT_DELIVERY_STATE.SENDING,
          },
        };
      }

      transaction.set(
        ref,
        {
          persistedDeliveryState: EXPORT_DELIVERY_STATE.SENDING,
          deliveryLeaseExpiresAtEpochMillis: leaseExpiresAtEpochMillis,
          deliveryFailureCode: FieldValue.delete(),
          deliveryFailureMessage: FieldValue.delete(),
        },
        { merge: true },
      );
      return {
        kind: "proceed",
        manifest: {
          ...manifest,
          persistedDeliveryState: EXPORT_DELIVERY_STATE.SENDING,
          deliveryLeaseExpiresAtEpochMillis: leaseExpiresAtEpochMillis,
          deliveryFailureCode: undefined,
          deliveryFailureMessage: undefined,
        },
      };
    });
  }

  async markSent(uid: string, exportId: string, sentAtEpochMillis: number): Promise<void> {
    await this.document(uid, exportId).set(
      {
        persistedDeliveryState: EXPORT_DELIVERY_STATE.SENT,
        deliverySentAtEpochMillis: sentAtEpochMillis,
        deliveryFailureCode: FieldValue.delete(),
        deliveryFailureMessage: FieldValue.delete(),
        deliveryLeaseExpiresAtEpochMillis: FieldValue.delete(),
      },
      { merge: true },
    );
  }

  async markFailed(
    uid: string,
    exportId: string,
    failureCode: string,
    failureMessage: string,
  ): Promise<void> {
    await this.document(uid, exportId).set(
      {
        persistedDeliveryState: EXPORT_DELIVERY_STATE.FAILED,
        deliveryFailureCode: failureCode,
        deliveryFailureMessage: failureMessage,
        deliveryLeaseExpiresAtEpochMillis: FieldValue.delete(),
      },
      { merge: true },
    );
  }

  private document(uid: string, exportId: string) {
    return adminDb.collection("users").doc(uid).collection("export_history").doc(exportId);
  }
}

function toManifest(
  data: Record<string, unknown> | undefined,
  exportId: string,
  uid: string,
): ExportManifest {
  if (data == null) {
    throw new HttpsError("internal", `Export ${exportId} has no manifest data.`);
  }
  return {
    exportId: readString(data.exportId) || exportId,
    uid: readString(data.uid) || uid,
    fileName: readRequiredString(data.fileName, "fileName"),
    sizeBytes: readNumber(data.sizeBytes),
    createdAtEpochMillis: readNumber(data.createdAtEpochMillis),
    displayLocation: readString(data.displayLocation),
    formats: readStringArray(data.formats),
    dateRange: readObject(data.dateRange),
    aircraft: readAircraftArray(data.aircraft),
    remoteArchiveRef: readNullableString(data.remoteArchiveRef),
    destinationEmail: readNullableString(data.destinationEmail),
    destinationEmailSource: readNullableString(data.destinationEmailSource),
    persistedDeliveryState: readDeliveryState(data.persistedDeliveryState),
    deliverySentAtEpochMillis: readOptionalNumber(data.deliverySentAtEpochMillis),
    deliveryFailureCode: readNullableString(data.deliveryFailureCode),
    deliveryFailureMessage: readNullableString(data.deliveryFailureMessage),
    remoteExpiresAtEpochMillis: readOptionalNumber(data.remoteExpiresAtEpochMillis),
    deliveryLeaseExpiresAtEpochMillis: readOptionalNumber(data.deliveryLeaseExpiresAtEpochMillis),
  };
}

function readRequiredString(value: unknown, field: string): string {
  const stringValue = readString(value);
  if (stringValue.length === 0) {
    throw new HttpsError("failed-precondition", `Export manifest is missing ${field}.`);
  }
  return stringValue;
}

function readString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function readNullableString(value: unknown): string | null {
  const stringValue = readString(value);
  return stringValue.length > 0 ? stringValue : null;
}

function readStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function readAircraftArray(value: unknown): ExportManifest["aircraft"] {
  if (!Array.isArray(value)) return [];
  return value.flatMap(item => {
    if (typeof item !== "object" || item == null) return [];
    const record = item as Record<string, unknown>;
    return [{
      tailNumber: readString(record.tailNumber),
      makeModel: readString(record.makeModel),
    }];
  });
}

function readObject(value: unknown): ExportManifest["dateRange"] {
  if (typeof value !== "object" || value == null) return null;
  const record = value as Record<string, unknown>;
  return {
    kind: readString(record.kind),
    months: readOptionalNumber(record.months) ?? 0,
    customStart: readString(record.customStart),
    customEnd: readString(record.customEnd),
  };
}

function readNumber(value: unknown): number {
  return readOptionalNumber(value) ?? 0;
}

function readOptionalNumber(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (value instanceof Timestamp) return value.toMillis();
  return null;
}

function readDeliveryState(value: unknown): ExportManifest["persistedDeliveryState"] {
  const state = readString(value);
  switch (state) {
    case EXPORT_DELIVERY_STATE.NOT_REQUESTED:
    case EXPORT_DELIVERY_STATE.QUEUED:
    case EXPORT_DELIVERY_STATE.SENDING:
    case EXPORT_DELIVERY_STATE.SENT:
    case EXPORT_DELIVERY_STATE.FAILED:
      return state;
    default:
      return EXPORT_DELIVERY_STATE.NOT_REQUESTED;
  }
}
