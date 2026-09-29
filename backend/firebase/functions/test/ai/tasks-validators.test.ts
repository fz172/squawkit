import { describe, expect, it } from "vitest";

import type { FlatRule } from "../../src/ai/tasks/stageTypes.js";
import type { Draft } from "../../src/ai/tasks/validate/types.js";
import {
  citationHolds,
  citationRule,
  confidenceRule,
  containsVerbatim,
  dedupIdsRule,
  lastDoneRule,
  meterRule,
  preselectRule,
  regulatoryRule,
  schemaRule,
  validate,
} from "../../src/ai/tasks/validate/validators.js";
import { airplaneContext, doc, draft, input, page } from "./fixtures.js";

const NULL_RULE: FlatRule = {
  kind: "on_condition",
  every: null,
  unit: null,
  meterKey: null,
  interval: null,
  months: null,
  dayOfMonth: null,
  description: null,
};

function typed(rules: Partial<FlatRule>[]): Draft {
  return schemaRule([draft({ rawRules: rules.map((r) => ({ ...NULL_RULE, ...r })) })], input())[0];
}

describe("1. schema rule", () => {
  it.each([
    ["time", { kind: "time", every: 12, unit: "months" }, { kind: "time", every: 12, unit: "months" }],
    ["time rounds", { kind: "time", every: 5.6, unit: "years" }, { kind: "time", every: 6, unit: "years" }],
    ["meter", { kind: "meter", meterKey: "engine_hours", interval: 50 }, { kind: "meter", meterKey: "engine_hours", interval: 50 }],
    ["seasonal sorts and dedups", { kind: "seasonal", months: [10, 4, 4] }, { kind: "seasonal", months: [4, 10], dayOfMonth: 0 }],
    ["on condition", { kind: "on_condition", description: "When worn" }, { kind: "on_condition", description: "When worn" }],
  ] as const)("types a %s rule", (_name, raw, expected) => {
    expect(typed([raw as Partial<FlatRule>]).rules).toEqual([expected]);
  });

  it.each([
    ["time with no unit", { kind: "time", every: 12 }],
    ["time of zero", { kind: "time", every: 0, unit: "days" }],
    ["meter with no key", { kind: "meter", interval: 50 }],
    ["negative meter interval", { kind: "meter", meterKey: "engine_hours", interval: -1 }],
    ["seasonal month 13", { kind: "seasonal", months: [13] }],
    ["seasonal day 40", { kind: "seasonal", months: [4], dayOfMonth: 40 }],
  ] as const)("drops a malformed rule: %s", (_name, raw) => {
    expect(typed([raw as Partial<FlatRule>]).rules).toEqual([]);
  });

  it("drops an untitled suggestion and files an unknown slot at Thing level", () => {
    const out = schemaRule(
      [draft({ title: "  " }), draft({ componentSlotKey: "rotor", componentHint: "Rotor #2" })],
      input(),
    );
    expect(out).toHaveLength(1);
    expect(out[0].componentSlotKey).toBe("");
    expect(out[0].componentHint).toBe("");
  });
});

describe("2. meter rule", () => {
  it("drops an unknown meter and keeps the rest", () => {
    const d = draft({
      rules: [
        { kind: "meter", meterKey: "cycles", interval: 500 },
        { kind: "time", every: 12, unit: "months" },
      ],
    });
    expect(meterRule([d], input())[0].rules).toEqual([{ kind: "time", every: 12, unit: "months" }]);
  });

  it("turns a task left with no rule into on-condition naming the interval", () => {
    const d = draft({ rules: [{ kind: "meter", meterKey: "landing_cycles", interval: 500 }] });
    expect(meterRule([d], input())[0].rules).toEqual([
      { kind: "on_condition", description: "Every 500 landing cycles" },
    ]);
  });
});

describe("3. regulatory rule", () => {
  const ad = (over: Partial<Draft> = {}) =>
    draft({
      type: "airworthiness_directive",
      referenceNumber: "AD 2024-05-07",
      complianceAuthority: "FAA",
      ...over,
    });
  const adDoc = doc({
    docType: "airworthiness_directive",
    pages: [page(1, "AIRWORTHINESS DIRECTIVE  AD  2024-05-07 applies to Rotax 915 iS engines.")],
  });

  it.each([
    ["no documents in the run", ad(), input({ documents: [] }), "routine"],
    ["the cited document is not a directive", ad(), input(), "routine"],
    ["the number is not in the document", ad({ referenceNumber: "AD 2023-01-01" }), input({ documents: [adDoc] }), "routine"],
    ["the directive prints the number (whitespace and case differ)", ad({ referenceNumber: "ad 2024-05-07" }), input({ documents: [adDoc] }), "airworthiness_directive"],
    ["a manufacturer-schedule item claims a bulletin", ad({ type: "service_bulletin", sourceKind: "manufacturer_schedule", evidence: { documentIndex: null, pages: [], sourceFigures: [] } }), input({ documents: [adDoc] }), "routine"],
  ] as const)("%s → %s", (_name, d, inp, expected) => {
    const out = regulatoryRule([d], inp)[0];
    expect(out.type).toBe(expected);
    if (expected === "routine") {
      expect(out.referenceNumber).toBe("");
      expect(out.complianceAuthority).toBe("");
    }
  });

  it("clears a routine item's reference when the run has no documents", () => {
    const out = regulatoryRule([draft({ referenceNumber: "SI 1014M" })], input({ documents: [] }))[0];
    expect(out.referenceNumber).toBe("");
  });
});

describe("4. citation rule", () => {
  it.each([
    ["the cited page states it", draft(), true],
    ["the cited page is another page", draft({ evidence: { documentIndex: 0, pages: [1], sourceFigures: [200] } }), false],
    ["no pages cited", draft({ evidence: { documentIndex: 0, pages: [], sourceFigures: [200] } }), false],
    ["no document", draft({ evidence: { documentIndex: 3, pages: [2], sourceFigures: [200] } }), false],
    ["a common-practice item needs no page", draft({ sourceKind: "common_practice", evidence: { documentIndex: null, pages: [], sourceFigures: [] } }), true],
  ] as const)("%s → kept %s", (_name, d, kept) => {
    expect(citationRule([d], input())).toHaveLength(kept ? 1 : 0);
  });

  it.each([
    ["half the title words", "Replace the spark plugs and leads", "replace spark plugs", [], true],
    ["one word plus a figure with a thousands separator", "Coolant replacement", "replace coolant every 10,000 km", [10000], true],
    ["a figure alone", "Coolant replacement", "every 10000 km", [10000], false],
    ["neither", "Valve clearance check", "lubricate the throttle cable", [200], false],
    ["one word plus a figure", "Valve clearance adjustment check", "valve at 200 h", [200], true],
    ["a figure inside a longer number is not a match", "Valve clearance adjustment check", "valve at 1200 h", [200], false],
  ] as const)("citationHolds: %s", (_name, title, text, figures, holds) => {
    expect(citationHolds(text, title, [...figures])).toBe(holds);
  });
});

describe("5. dedup ids rule", () => {
  it.each([
    ["a known task id", "task-annual", "task-annual"],
    ["an unknown task id", "task-nope", ""],
  ] as const)("keeps only %s", (_name, id, expected) => {
    const out = dedupIdsRule([draft({ matchesExistingTaskId: id, intervalDifferenceNote: "6 vs 12" })], input())[0];
    expect(out.matchesExistingTaskId).toBe(expected);
    expect(out.intervalDifferenceNote).toBe(expected ? "6 vs 12" : "");
  });

  it.each([
    [0, 0],
    [1, 1],
    [2, -1],
    [-5, -1],
  ])("static index %i → %i", (index, expected) => {
    expect(dedupIdsRule([draft({ mergesStaticIndex: index })], input())[0].mergesStaticIndex).toBe(expected);
  });
});

describe("last-done rule", () => {
  it("fills date and the matching reading from the log", () => {
    const d = draft({ lastDoneLogId: "log-1", rules: [{ kind: "meter", meterKey: "engine_hours", interval: 50 }] });
    expect(lastDoneRule([d], input())[0].lastDone).toEqual({
      logId: "log-1",
      date: "2026-05-02",
      reading: { meterKey: "engine_hours", value: 380 },
    });
  });

  it("drops a log id the context does not hold", () => {
    expect(lastDoneRule([draft({ lastDoneLogId: "log-9" })], input())[0].lastDone).toBeNull();
  });
});

describe("6. pre-selection rule", () => {
  const home = input({ context: airplaneContext({ templateId: "home" }) });
  const recall = { sourceKind: "common_practice" as const, evidence: { documentIndex: null, pages: [], sourceFigures: [] } };
  it.each([
    ["document", draft(), input(), true],
    ["logs", draft({ sourceKind: "logs", evidence: { documentIndex: null, pages: [], sourceFigures: [] } }), input(), true],
    ["common practice on an airplane", draft(recall), input(), false],
    ["common practice on a home", draft(recall), home, true],
    ["a document for a different Thing", draft(), input({ documents: [doc({ matchesThing: false })] }), false],
    ["already tracked", draft({ matchesExistingTaskId: "task-annual" }), input(), false],
  ] as const)("%s → %s", (_name, d, inp, expected) => {
    expect(preselectRule([d], inp)[0].preselect).toBe(expected);
  });
});

describe("7. confidence rule", () => {
  it("drops low-confidence items individually", () => {
    const out = confidenceRule([draft(), draft({ confidence: "low" }), draft({ confidence: "medium" })], input());
    expect(out.map((d) => d.confidence)).toEqual(["high", "medium"]);
  });
});

describe("validate", () => {
  it("runs every rule in order", () => {
    const out = validate(
      [
        draft({ lastDoneLogId: "log-1" }),
        draft({ title: "Invented item", evidence: { documentIndex: 0, pages: [1], sourceFigures: [7] } }),
      ],
      input(),
    );
    expect(out).toHaveLength(1);
    expect(out[0].rules).toEqual([{ kind: "meter", meterKey: "engine_hours", interval: 200 }]);
    expect(out[0].lastDone?.logId).toBe("log-1");
    expect(out[0].preselect).toBe(true);
  });

  it("containsVerbatim ignores whitespace and case only", () => {
    expect(containsVerbatim("SB-915 i-001 R2", "sb-915i-001r2")).toBe(true);
    expect(containsVerbatim("SB-915 i-001", "SB-915 i-002")).toBe(false);
    expect(containsVerbatim("anything", "  ")).toBe(false);
  });
});
