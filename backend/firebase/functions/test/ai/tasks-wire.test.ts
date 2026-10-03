import { describe, expect, it } from "vitest";

import type { SuggestTasksResult, TaskSuggestion } from "../../src/ai/tasks/model.js";
import { requestFromProto, resultToProto, ruleFromProto, ruleToProto } from "../../src/ai/tasks/wire.js";
import {
  DocType,
  SuggestTasksRequest as SuggestTasksRequestProto,
  SuggestTasksResult as SuggestTasksResultProto,
} from "../../src/generated/proto/rpc/suggest_tasks/suggest_tasks.js";
import { ComplianceType } from "../../src/generated/proto/task/maintenance_task.js";
import { TaskOriginKind, TaskSourceKind } from "../../src/generated/proto/task/task_origin.js";
import { airplaneContext } from "./fixtures.js";

/** The wire form of fixtures.ts's airplane, as the app's SuggestionContextBuilder will send it. */
function airplaneRequestProto(): SuggestTasksRequestProto {
  return SuggestTasksRequestProto.fromPartial({
    thingId: { value: "thing-1" },
    hostUid: { value: "host" },
    entryPoint: "overview",
    context: {
      templateId: { value: "airplane" },
      templateVersion: 13,
      specs: [
        { key: "make", label: "Make", value: "Sling" },
        { key: "model", label: "Model", value: "TSi" },
        { key: "tail_number", label: "Tail number", value: "N123AB" },
      ],
      components: [{ slotKey: "engine", make: "Rotax", model: "915 iS", spec: [] }],
      meters: [
        { key: "airframe_hours", unitLabel: "hrs", componentSlotKey: "", current: 412, hasCurrent: true },
        { key: "engine_hours", unitLabel: "hrs", componentSlotKey: "engine", current: 410, hasCurrent: true },
      ],
      existingTasks: [
        {
          id: { value: "task-annual" },
          title: "Annual inspection",
          componentSlotKey: "",
          rules: [{ timeRule: { intervalMonths: 12 } }],
          type: ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
          referenceNumber: "",
        },
      ],
      logs: [
        {
          id: { value: "log-1" },
          date: "2026-05-02",
          readings: [{ meterKey: "engine_hours", value: 380 }],
          title: "Oil change",
          workDescription: "Changed oil and filter",
          componentSlotKey: "engine",
        },
      ],
      logsTruncated: false,
      lexiconTaskNoun: "inspection",
    },
  });
}

describe("requestFromProto", () => {
  it("decodes the wire request into exactly the pipeline's context", () => {
    // Through bytes, as the worker receives it.
    const bytes = SuggestTasksRequestProto.encode(airplaneRequestProto()).finish();

    expect(requestFromProto(SuggestTasksRequestProto.decode(bytes))).toEqual({
      thingId: "thing-1",
      // The app sends no starter pack since #1265; the server's curated list fills it later.
      context: { ...airplaneContext(), staticPack: [] },
      documents: [],
    });
  });

  it("reads an unlogged meter as null, not zero", () => {
    const proto = airplaneRequestProto();
    proto.context!.meters[0] = { ...proto.context!.meters[0], current: 0, hasCurrent: false };

    expect(requestFromProto(proto).context.meters[0].current).toBeNull();
  });
});

describe("rules", () => {
  it.each([
    [{ kind: "time", every: 30, unit: "days" }],
    [{ kind: "time", every: 6, unit: "months" }],
    [{ kind: "time", every: 2, unit: "years" }],
    [{ kind: "meter", meterKey: "engine_hours", interval: 100 }],
    [{ kind: "seasonal", months: [4, 10], dayOfMonth: 15 }],
    [{ kind: "on_condition", description: "When worn" }],
  ] as const)("round-trips %o", (rule) => {
    expect(ruleFromProto(ruleToProto(rule))).toEqual([rule]);
  });

  it("leaves the creation date and anniversary convention for the client to stamp", () => {
    expect(ruleToProto({ kind: "time", every: 6, unit: "months" }).timeRule).toMatchObject({
      creationDate: undefined,
      dueOnAnniversary: false,
    });
  });

  it("drops linked, immediate and empty time rules, which have no flat form", () => {
    expect(ruleFromProto({ linkedRule: { parentInspectionId: "x" } })).toEqual([]);
    expect(ruleFromProto({ immediateRule: {} })).toEqual([]);
    expect(
      ruleFromProto({
        timeRule: { intervalDays: 0, intervalMonths: 0, intervalYears: 0, creationDate: undefined, dueOnAnniversary: false },
      }),
    ).toEqual([]);
  });
});

describe("resultToProto", () => {
  const suggestion: TaskSuggestion = {
    suggestionId: "s1",
    title: "Replace spark plugs",
    rationale: "Rotax recommends it.",
    description: "",
    componentSlotKey: "engine",
    componentHint: "",
    rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }],
    isOneTime: false,
    firstDue: null,
    type: "airworthiness_directive",
    referenceNumber: "AD 2024-05-07",
    complianceAuthority: "FAA",
    sourceKind: "document",
    citation: "AD 2024-05-07",
    pageRef: "p. 2",
    sourcePages: [2],
    sourceDocument: "blob-ad",
    lastDone: { logId: "log-1", date: "2026-05-02", reading: { meterKey: "engine_hours", value: 380 } },
    matchesExistingTaskId: "",
    intervalDifferenceNote: "",
    mergesStaticIndex: -1,
    preselect: true,
  };
  const result: SuggestTasksResult = {
    suggestions: [suggestion, { ...suggestion, suggestionId: "s2", sourceDocument: "", lastDone: null, firstDue: { date: "2026-12-01", meter: null }, matchesExistingTaskId: "task-annual" }],
    documents: [
      {
        blobId: "blob-ad",
        name: "ad.pdf",
        manufacturer: "FAA",
        title: "AD 2024-05-07",
        revision: "",
        docType: "airworthiness_directive",
        matchesThing: true,
      },
    ],
    generationVersion: "tasks-4",
  };

  it("encodes the result, surviving a trip through bytes", () => {
    const decoded = SuggestTasksResultProto.decode(SuggestTasksResultProto.encode(resultToProto(result)).finish());

    expect(decoded.generationVersion).toBe("tasks-4");
    const [first, second] = decoded.suggestions;
    expect(first).toMatchObject({
      suggestionId: { value: "s1" },
      type: ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE,
      sourceKind: TaskSourceKind.TASK_SOURCE_KIND_DOCUMENT,
      sourceDocument: { value: "blob-ad" },
      sourcePages: [2],
      rules: [{ meterRule: { meterKey: "engine_hours", interval: 200 } }],
      lastDone: { logId: { value: "log-1" }, date: "2026-05-02", reading: { meterKey: "engine_hours", value: 380 } },
      matchesExistingTaskId: undefined,
      preselect: true,
      originKind: TaskOriginKind.TASK_ORIGIN_KIND_AI_DOCUMENT,
    });
    expect(second).toMatchObject({
      sourceDocument: undefined,
      originKind: TaskOriginKind.TASK_ORIGIN_KIND_AI_THING,
      lastDone: undefined,
      firstDue: { date: "2026-12-01", meter: undefined },
      matchesExistingTaskId: { value: "task-annual" },
    });
    expect(decoded.documents).toEqual([
      expect.objectContaining({ blobId: { value: "blob-ad" }, docType: DocType.DOC_TYPE_AIRWORTHINESS_DIRECTIVE, matchesThing: true }),
    ]);
  });
});
