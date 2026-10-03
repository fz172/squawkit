import { DocType, SuggestTasksResult as SuggestTasksResultProto } from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import type {
  SuggestTasksRequest as SuggestTasksRequestProto,
  TaskSuggestion as TaskSuggestionProto,
} from "../../generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { ComplianceType, type InspectionRule } from "../../generated/proto/task/maintenance_task.js";
import { TaskOriginKind, TaskSourceKind } from "../../generated/proto/task/task_origin.js";
import type { MeterReading } from "../../generated/proto/thing/meter_reading.js";
import type {
  ComplianceKind,
  DocType as DocTypeName,
  MeterReadingValue,
  SuggestTasksRequest,
  SuggestTasksResult,
  SuggestedRule,
  TaskSourceKind as TaskSourceKindName,
} from "./model.js";

/**
 * Between the wire protos (`rpc/suggest_tasks`, design §4.2) and the pipeline's plain types
 * (`model.ts`), which the eval writes as readable JSON. Ids are boxed on the wire and plain here;
 * rules are the app's `InspectionRule` oneof on the wire and a flat union here.
 */

export function requestFromProto(proto: SuggestTasksRequestProto): SuggestTasksRequest {
  const c = proto.context;
  return {
    thingId: proto.thingId?.value ?? "",
    context: {
      templateId: c?.templateId?.value ?? "",
      templateVersion: c?.templateVersion ?? 0,
      specs: (c?.specs ?? []).map((s) => ({ key: s.key, label: s.label, value: s.value })),
      components: (c?.components ?? []).map((comp) => ({
        slotKey: comp.slotKey,
        make: comp.make,
        model: comp.model,
        spec: comp.spec.map((s) => ({ key: s.key, label: s.label, value: s.value })),
      })),
      meters: (c?.meters ?? []).map((m) => ({
        key: m.key,
        unitLabel: m.unitLabel,
        componentSlotKey: m.componentSlotKey,
        current: m.hasCurrent ? m.current : null,
      })),
      existingTasks: (c?.existingTasks ?? []).map((t) => ({
        id: t.id?.value ?? "",
        title: t.title,
        componentSlotKey: t.componentSlotKey,
        rules: t.rules.flatMap(ruleFromProto),
        type: complianceKindFromProto(t.type),
        referenceNumber: t.referenceNumber,
      })),
      logs: (c?.logs ?? []).map((l) => ({
        id: l.id?.value ?? "",
        date: l.date,
        readings: l.readings.map(readingFromProto),
        title: l.title,
        workDescription: l.workDescription,
        componentSlotKey: l.componentSlotKey,
      })),
      logsTruncated: c?.logsTruncated ?? false,
      // The app no longer sends its starter pack; the curated list fills this from the server's
      // own files (#1265, design §6.8).
      staticPack: [],
      lexiconTaskNoun: c?.lexiconTaskNoun ?? "",
    },
    documents: proto.documents.map((d) => ({
      blobId: d.blobId?.value ?? "",
      name: d.name,
      mimeType: d.mimeType,
      sha256: d.sha256,
      sizeBytes: d.sizeBytes,
    })),
  };
}

export function resultToProto(result: SuggestTasksResult): SuggestTasksResultProto {
  return SuggestTasksResultProto.fromPartial({
    suggestions: result.suggestions.map(
      (s): Partial<TaskSuggestionProto> => ({
        suggestionId: { value: s.suggestionId },
        title: s.title,
        rationale: s.rationale,
        description: s.description,
        componentSlotKey: s.componentSlotKey,
        componentHint: s.componentHint,
        rules: s.rules.map(ruleToProto),
        isOneTime: s.isOneTime,
        firstDue: s.firstDue
          ? { date: s.firstDue.date ?? "", meter: s.firstDue.meter ? readingToProto(s.firstDue.meter) : undefined }
          : undefined,
        type: complianceKindToProto(s.type),
        referenceNumber: s.referenceNumber,
        complianceAuthority: s.complianceAuthority,
        sourceKind: SOURCE_KINDS[s.sourceKind],
        citation: s.citation,
        pageRef: s.pageRef,
        sourcePages: s.sourcePages,
        sourceDocument: s.sourceDocument ? { value: s.sourceDocument } : undefined,
        lastDone: s.lastDone
          ? {
              logId: { value: s.lastDone.logId },
              date: s.lastDone.date,
              reading: s.lastDone.reading ? readingToProto(s.lastDone.reading) : undefined,
            }
          : undefined,
        matchesExistingTaskId: s.matchesExistingTaskId ? { value: s.matchesExistingTaskId } : undefined,
        intervalDifferenceNote: s.intervalDifferenceNote,
        preselect: s.preselect,
        // Every suggestion the pipeline writes is the model's; curated items join in #1265.
        originKind: s.sourceDocument ? TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT : TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
      }),
    ),
    documents: result.documents.map((d) => ({
      blobId: { value: d.blobId },
      name: d.name,
      manufacturer: d.manufacturer,
      title: d.title,
      revision: d.revision,
      docType: DOC_TYPES[d.docType],
      matchesThing: d.matchesThing,
    })),
    generationVersion: result.generationVersion,
  });
}

/**
 * One wire rule as the pipeline's flat rule, or nothing. Linked and immediate rules have no flat
 * form; they only appear on existing tasks, where the model needs the title and schedule rather
 * than the link. A time rule with no interval set is the legacy months-only shape of zero.
 */
export function ruleFromProto(rule: InspectionRule): SuggestedRule[] {
  const t = rule.timeRule;
  if (t) {
    if (t.intervalDays > 0) return [{ kind: "time", every: t.intervalDays, unit: "days" }];
    if (t.intervalYears > 0) return [{ kind: "time", every: t.intervalYears, unit: "years" }];
    if (t.intervalMonths > 0) return [{ kind: "time", every: t.intervalMonths, unit: "months" }];
    return [];
  }
  if (rule.meterRule) return [{ kind: "meter", meterKey: rule.meterRule.meterKey, interval: rule.meterRule.interval }];
  if (rule.seasonalRule) {
    return [{ kind: "seasonal", months: rule.seasonalRule.months, dayOfMonth: rule.seasonalRule.dayOfMonth }];
  }
  if (rule.onConditionRule) return [{ kind: "on_condition", description: rule.onConditionRule.description }];
  return [];
}

/**
 * A suggested rule as the app's `InspectionRule`. The time rule's `creationDate` and
 * `dueOnAnniversary` are left unset: the client stamps both when the suggestion is accepted, as the
 * task form does (design §7.4).
 */
export function ruleToProto(rule: SuggestedRule): InspectionRule {
  switch (rule.kind) {
    case "time":
      return {
        timeRule: {
          intervalDays: rule.unit === "days" ? rule.every : 0,
          intervalMonths: rule.unit === "months" ? rule.every : 0,
          intervalYears: rule.unit === "years" ? rule.every : 0,
          creationDate: undefined,
          dueOnAnniversary: false,
        },
      };
    case "meter":
      return { meterRule: { meterKey: rule.meterKey, interval: rule.interval } };
    case "seasonal":
      return { seasonalRule: { months: rule.months, dayOfMonth: rule.dayOfMonth } };
    case "on_condition":
      return { onConditionRule: { description: rule.description } };
  }
}

function readingFromProto(r: MeterReading): MeterReadingValue {
  return { meterKey: r.meterKey, value: r.value };
}

function readingToProto(r: MeterReadingValue): MeterReading {
  return { meterKey: r.meterKey, componentId: "", value: r.value };
}

function complianceKindFromProto(type: ComplianceType): ComplianceKind {
  switch (type) {
    case ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN:
      return "service_bulletin";
    case ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE:
      return "airworthiness_directive";
    default:
      return "routine";
  }
}

function complianceKindToProto(kind: ComplianceKind): ComplianceType {
  switch (kind) {
    case "service_bulletin":
      return ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN;
    case "airworthiness_directive":
      return ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE;
    case "routine":
      return ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION;
  }
}

const SOURCE_KINDS: Record<TaskSourceKindName, TaskSourceKind> = {
  document: TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT,
  manufacturer_schedule: TaskSourceKind.TASK_SOURCE_KIND_MANUFACTURER_SCHEDULE,
  common_practice: TaskSourceKind.TASK_SOURCE_KIND_COMMON_PRACTICE,
  logs: TaskSourceKind.TASK_SOURCE_KIND_LOGS,
};

const DOC_TYPES: Record<DocTypeName, DocType> = {
  maintenance_manual: DocType.DOC_TYPE_MAINTENANCE_MANUAL,
  owners_manual: DocType.DOC_TYPE_OWNERS_MANUAL,
  service_bulletin: DocType.DOC_TYPE_SERVICE_BULLETIN,
  service_instruction: DocType.DOC_TYPE_SERVICE_INSTRUCTION,
  airworthiness_directive: DocType.DOC_TYPE_AIRWORTHINESS_DIRECTIVE,
  appliance_manual: DocType.DOC_TYPE_APPLIANCE_MANUAL,
  other: DocType.DOC_TYPE_OTHER,
};
