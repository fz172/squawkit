export const EXPORT_DELIVERY_STATE = {
  NOT_REQUESTED: "NOT_REQUESTED",
  QUEUED: "QUEUED",
  SENDING: "SENDING",
  SENT: "SENT",
  FAILED: "FAILED",
} as const;

export type ExportDeliveryState =
  (typeof EXPORT_DELIVERY_STATE)[keyof typeof EXPORT_DELIVERY_STATE];

export type ExportManifest = {
  exportId: string;
  uid: string;
  fileName: string;
  sizeBytes: number;
  createdAtEpochMillis: number;
  displayLocation: string;
  formats: string[];
  dateRange?: ExportManifestDateRange | null;
  aircraft: ExportManifestAircraft[];
  remoteArchiveRef?: string | null;
  destinationEmail?: string | null;
  destinationEmailSource?: string | null;
  persistedDeliveryState: ExportDeliveryState;
  deliverySentAtEpochMillis?: number | null;
  deliveryFailureCode?: string | null;
  deliveryFailureMessage?: string | null;
  remoteExpiresAtEpochMillis?: number | null;
  deliveryLeaseExpiresAtEpochMillis?: number | null;
};

export type ExportManifestDateRange = {
  kind: string;
  months?: number;
  customStart?: string;
  customEnd?: string;
};

export type ExportManifestAircraft = {
  tailNumber: string;
  makeModel: string;
};

export type DeliveryDispatchResult = {
  requestOutcome: "sent" | "already-sent" | "in-progress" | "failed" | "resend-throttled";
  persistedDeliveryState: ExportDeliveryState;
  deliverySentAtEpochMillis?: number;
  deliveryFailureCode?: string;
  deliveryFailureMessage?: string;
};
