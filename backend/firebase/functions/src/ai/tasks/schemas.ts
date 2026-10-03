import type { JsonSchema } from "../providers/types.js";

/** Structured-output schemas for each stage, in the portable subset (assertPortableSchema). */

const STRING: JsonSchema = { type: "string" };
const NUMBER: JsonSchema = { type: "number" };
const INTEGER: JsonSchema = { type: "integer" };
const BOOLEAN: JsonSchema = { type: "boolean" };
const NULLABLE_STRING: JsonSchema = { type: ["string", "null"] };

function object(properties: Record<string, JsonSchema>): JsonSchema {
  return {
    type: "object",
    properties,
    required: Object.keys(properties),
    additionalProperties: false,
  };
}

function array(items: JsonSchema): JsonSchema {
  return { type: "array", items };
}

function oneOf(values: string[]): JsonSchema {
  return { type: "string", enum: values };
}

function nullable(schema: JsonSchema): JsonSchema {
  return { anyOf: [schema, { type: "null" }] };
}

const INTERVAL = object({
  value: NUMBER,
  unit: oneOf([
    "hours",
    "days",
    "weeks",
    "months",
    "years",
    "miles",
    "kilometers",
    "cycles",
    "landings",
    "starts",
    "other",
  ]),
});

const COMPLIANCE = oneOf(["routine", "service_bulletin", "airworthiness_directive"]);
const CONFIDENCE = oneOf(["high", "medium", "low"]);

export const LOCATE_SCHEMA = object({ pages: array(INTEGER) });

export const EXTRACT_SCHEMA = object({
  document: object({
    manufacturer: STRING,
    models: array(STRING),
    title: STRING,
    revision: NULLABLE_STRING,
    docType: oneOf([
      "maintenance_manual",
      "owners_manual",
      "service_bulletin",
      "service_instruction",
      "airworthiness_directive",
      "appliance_manual",
      "other",
    ]),
    referenceNumber: NULLABLE_STRING,
  }),
  items: array(
    object({
      title: STRING,
      description: STRING,
      checklist: array(STRING),
      intervals: array(INTERVAL),
      isOneTime: BOOLEAN,
      pages: array(INTEGER),
      printedPageRef: NULLABLE_STRING,
      componentHint: NULLABLE_STRING,
      type: COMPLIANCE,
      referenceNumber: NULLABLE_STRING,
      complianceAuthority: NULLABLE_STRING,
    }),
  ),
});

export const RECALL_SCHEMA = object({
  identityConfidence: CONFIDENCE,
  items: array(
    object({
      title: STRING,
      description: STRING,
      checklist: array(STRING),
      intervals: array(INTERVAL),
      isOneTime: BOOLEAN,
      componentHint: NULLABLE_STRING,
      sourceKind: oneOf(["manufacturer_schedule", "common_practice"]),
      publication: NULLABLE_STRING,
    }),
  ),
});

const FLAT_RULE = object({
  kind: oneOf(["time", "meter", "seasonal", "on_condition"]),
  every: nullable(NUMBER),
  unit: nullable(oneOf(["days", "months", "years"])),
  meterKey: NULLABLE_STRING,
  interval: nullable(NUMBER),
  months: nullable(array(INTEGER)),
  dayOfMonth: nullable(INTEGER),
  description: NULLABLE_STRING,
});

const FIRST_DUE = object({
  anchor: oneOf(["meter_reading", "meter_from_now", "time_from_now"]),
  meterKey: NULLABLE_STRING,
  value: NUMBER,
  unit: nullable(oneOf(["days", "months", "years"])),
});

export const TAILOR_SCHEMA = object({
  suggestions: array(
    object({
      candidateIds: array(STRING),
      title: STRING,
      rationale: STRING,
      description: STRING,
      componentSlotKey: NULLABLE_STRING,
      componentHint: NULLABLE_STRING,
      rules: array(FLAT_RULE),
      isOneTime: BOOLEAN,
      firstDue: array(FIRST_DUE),
      lastDoneLogId: NULLABLE_STRING,
      matchesExistingTaskId: NULLABLE_STRING,
      intervalDifferenceNote: NULLABLE_STRING,
      mergesStaticIndex: nullable(INTEGER),
      confidence: CONFIDENCE,
    }),
  ),
  documents: array(object({ index: INTEGER, matchesThing: BOOLEAN })),
  notApplicable: array(object({ index: INTEGER, reason: STRING })),
});
