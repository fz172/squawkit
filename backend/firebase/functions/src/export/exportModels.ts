export const EXPORT_DELIVERY_STATE = {
  NOT_IMPLEMENTED: "NOT_IMPLEMENTED",
} as const;

export type ExportDeliveryState =
  (typeof EXPORT_DELIVERY_STATE)[keyof typeof EXPORT_DELIVERY_STATE];
