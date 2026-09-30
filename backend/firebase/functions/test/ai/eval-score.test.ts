import { describe, expect, it } from "vitest";

import { checkExpected, type EvalCase, type Expected } from "../../eval/src/caseFormat.js";
import { BAR, renderReport, summarize } from "../../eval/src/report.js";
import { rulesMatch, scoreCase, titleSimilarity, type ScoreInput } from "../../eval/src/score.js";
import type { TaskSuggestion } from "../../src/ai/tasks/model.js";
import { GENERATION_VERSION } from "../../src/ai/tasks/version.js";
import { airplaneContext, page } from "./fixtures.js";

function suggestion(over: Partial<TaskSuggestion>): TaskSuggestion {
  return {
    suggestionId: "s1",
    title: "Replace spark plugs",
    rationale: "",
    description: "",
    componentSlotKey: "engine",
    componentHint: "",
    rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }],
    isOneTime: false,
    firstDue: null,
    type: "routine",
    referenceNumber: "",
    complianceAuthority: "",
    sourceKind: "document",
    citation: "Rotax MM",
    pageRef: "p. 2",
    sourcePages: [2],
    sourceDocument: "blob-mm",
    lastDone: null,
    matchesExistingTaskId: "",
    intervalDifferenceNote: "",
    mergesStaticIndex: -1,
    preselect: true,
    ...over,
  };
}

const CASE: EvalCase = {
  id: "rotax",
  description: "",
  kind: "document",
  request: { thingId: "t", context: airplaneContext(), documents: [] },
};

const PAGES = new Map([
  [
    "blob-mm",
    [
      page(1, "Rotax maintenance manual. Airworthiness directive AD 2024-05-07 index."),
      page(2, "Replace spark plugs every 200 hours. Check coolant every 12 months."),
      page(3, "Valve clearance: not adjustable."),
    ],
  ],
]);

function score(suggestions: TaskSuggestion[], expected: Partial<Expected> = {}, over: Partial<ScoreInput> = {}) {
  return scoreCase({
    evalCase: CASE,
    expected: { reviewed: true, tasks: [], ...expected },
    outcome: {
      status: "succeeded",
      result: {
        suggestions,
        documents: [
          {
            blobId: "blob-mm",
            name: "mm.pdf",
            manufacturer: "Rotax",
            title: "MM",
            revision: "",
            docType: "maintenance_manual",
            matchesThing: true,
          },
        ],
        generationVersion: GENERATION_VERSION,
      },
    },
    pagesByBlob: PAGES,
    calls: [
      {
        stage: "tailor",
        provider: "p",
        tier: "strong",
        usage: { inputTokens: 1, outputTokens: 1, costMicros: 250 },
        latencyMs: 1,
        attempts: 2,
        cacheHit: false,
        pages: 0,
      },
    ],
    latencyMs: 30_000,
    ...over,
  });
}

describe("matching", () => {
  it.each([
    ["spark plugs", "Replace spark plugs", 1],
    ["Replace spark plugs", "Spark plug replacement", 1 / 3],
    ["coolant change", "Replace coolant", 0.5],
    ["", "anything", 0],
  ])("titleSimilarity(%s, %s) = %f", (alias, title, expected) => {
    expect(titleSimilarity(alias, title)).toBeCloseTo(expected);
  });

  it("compares rules as sets, months with years, meters within 2%", () => {
    expect(rulesMatch([{ kind: "time", every: 1, unit: "years" }], [{ kind: "time", every: 12, unit: "months" }])).toBe(true);
    expect(rulesMatch([{ kind: "time", every: 30, unit: "days" }], [{ kind: "time", every: 1, unit: "months" }])).toBe(false);
    expect(
      rulesMatch(
        [{ kind: "meter", meterKey: "odometer", interval: 10000 }, { kind: "time", every: 12, unit: "months" }],
        [{ kind: "time", every: 12, unit: "months" }, { kind: "meter", meterKey: "odometer", interval: 9942 }],
      ),
    ).toBe(true);
    expect(rulesMatch([{ kind: "meter", meterKey: "odometer", interval: 10000 }], [{ kind: "meter", meterKey: "odometer", interval: 9000 }])).toBe(false);
    expect(rulesMatch([], [{ kind: "time", every: 12, unit: "months" }])).toBe(false);
  });
});

describe("scoreCase", () => {
  const expected: Partial<Expected> = {
    tasks: [
      { titleAliases: ["spark plugs"], rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }], citations: [{ document: "blob-mm", pages: [2] }] },
      { titleAliases: ["coolant"], rules: [{ kind: "time", every: 24, unit: "months" }], citations: [{ document: "blob-other", pages: [9] }, { document: "blob-mm", pages: [2] }] },
      { titleAliases: ["gearbox overhaul"], rules: [] },
      {
        titleAliases: ["oil change"],
        rules: [{ kind: "time", every: 6, unit: "months" }],
        alternativeRules: [[{ kind: "time", every: 12, unit: "months" }]],
      },
      { titleAliases: ["fuel filter"], rules: [], optional: true },
      { titleAliases: ["crankcase AD"], rules: [], mustNotAppear: true },
    ],
  };

  it("scores recall, intervals, citations, invented and forbidden items", () => {
    const s = score(
      [
        suggestion({ suggestionId: "s1" }),
        suggestion({ suggestionId: "s2", title: "Check coolant", rules: [{ kind: "time", every: 12, unit: "months" }], sourcePages: [3] }),
        suggestion({ suggestionId: "s3", title: "Inspect exhaust springs", sourcePages: [3] }),
        suggestion({ suggestionId: "s4", title: "Crankcase AD inspection", sourceKind: "common_practice", sourcePages: [] }),
        suggestion({ suggestionId: "s5", title: "Oil change", rules: [{ kind: "time", every: 1, unit: "years" }], sourcePages: [2] }),
      ],
      expected,
    );

    expect(s.expected).toBe(4);
    expect(s.matched).toBe(3);
    expect(s.recall).toBeCloseTo(3 / 4);
    expect(s.missed).toEqual(["gearbox overhaul"]);
    // Spark plugs and the oil change's alternative interval match; coolant does not.
    expect(s.intervalAccuracy).toBeCloseTo(2 / 3);
    // Coolant cites page 3, not the expected 2. The oil change has no citations, so the fallback
    // checks its cited page, which does not mention oil.
    expect(s.citationAccuracy).toBeCloseTo(1 / 3);
    expect(s.invented).toEqual(["Inspect exhaust springs"]);
    // Four document suggestions (s1, s2, s3, s5); s1, s2 and s5 match, s3 does not.
    expect(s.documentSuggestions).toBe(4);
    expect(s.precision).toBeCloseTo(3 / 4);
    expect(s.duplicates).toEqual([]);
    expect(s.forbidden).toEqual(["Crankcase AD inspection"]);
    expect(s.costMicros).toBe(250);
    expect(s.retries).toBe(1);
  });

  it("checks a one-time item's first-due reading with its interval", () => {
    const firstService = {
      tasks: [{ titleAliases: ["first service"], rules: [], firstDueMeter: { meterKey: "engine_hours", value: 25 } }],
    };
    const at = (value: number | null) =>
      score(
        [
          suggestion({
            title: "First service",
            rules: [],
            isOneTime: true,
            firstDue: value === null ? null : { date: null, meter: { meterKey: "engine_hours", value } },
            sourceKind: "common_practice",
          }),
        ],
        firstService,
      ).intervalAccuracy;
    expect(at(25)).toBe(1);
    expect(at(50)).toBe(0);
    expect(at(null)).toBe(0);
  });

  it("scores a regulation-only task's citation by the cited page", () => {
    const s = score([suggestion({ title: "Transponder test", rules: [{ kind: "time", every: 24, unit: "months" }] })], {
      tasks: [
        { titleAliases: ["transponder test"], rules: [{ kind: "time", every: 24, unit: "months" }], citations: [{ regulation: "14 CFR § 91.413" }] },
      ],
    });
    expect(s.intervalAccuracy).toBe(1);
    // Page 2 does not mention a transponder, so the fallback check fails the citation.
    expect(s.citationAccuracy).toBe(0);
  });

  it("tells a duplicate of a matched task from an extra", () => {
    const s = score(
      [
        suggestion({ suggestionId: "a", title: "Replace spark plugs" }),
        suggestion({ suggestionId: "b", title: "Spark plugs replacement" }),
        suggestion({ suggestionId: "c", title: "Inspect exhaust springs" }),
      ],
      { tasks: [{ titleAliases: ["spark plugs"], rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }] }] },
    );
    expect(s.precision).toBeCloseTo(1 / 3);
    expect(s.duplicates).toEqual(["Spark plugs replacement"]);
    expect(s.invented).toEqual(["Inspect exhaust springs"]);
  });

  it("finds each hard-gate violation", () => {
    const s = score([
      suggestion({ suggestionId: "a", type: "airworthiness_directive", referenceNumber: "AD 2024-05-07" }),
      suggestion({ suggestionId: "b", title: "Overhaul gearbox", referenceNumber: "SB-915-099" }),
      suggestion({ suggestionId: "c", title: "Lubricate the throttle cable", sourcePages: [3] }),
      suggestion({ suggestionId: "d", rules: [{ kind: "meter", meterKey: "cycles", interval: 500 }] }),
    ]);

    expect(s.gates.unsupportedRegulatory).toEqual(["Replace spark plugs"]);
    expect(s.gates.unverbatimReferences).toEqual(["Overhaul gearbox (SB-915-099)"]);
    expect(s.gates.uncitedDocumentItems).toEqual(["Overhaul gearbox", "Lubricate the throttle cable"]);
    expect(s.gates.foreignMeterKeys).toEqual(["Replace spark plugs: cycles"]);
    expect(s.gates.validOutput).toBe(true);
  });

  it("scores a failed run, and checks the expected status", () => {
    const failed = score([], { expectedStatus: "empty" }, { outcome: { status: "failed", errorCode: "invalid_output" } });
    expect(failed).toMatchObject({ status: "failed", statusOk: false, suggestions: 0, recall: null });
    expect(failed.gates.validOutput).toBe(false);
  });
});

describe("summary and report", () => {
  it("holds document cases to the bar and fails the gates on any violation", () => {
    const good = score([suggestion({})], {
      tasks: [{ titleAliases: ["spark plugs"], rules: [{ kind: "meter", meterKey: "engine_hours", interval: 200 }], citations: [{ document: "blob-mm", pages: [2] }] }],
    });
    const summary = summarize([good], [], new Map([["rotax", 3]]));
    expect(summary.document).toMatchObject({ recall: 1, intervalAccuracy: 1, citationAccuracy: 1, p90LatencyMs: 30_000, meetsBar: true });
    expect(summary.gates.passed).toBe(true);
    expect(summary.meanCostPerDocumentMicros).toBeCloseTo(250 / 3);

    const slow = summarize([{ ...good, latencyMs: BAR.p90LatencyMs + 1 }], [], new Map([["rotax", 3]]));
    expect(slow.document.meetsBar).toBe(false);

    const badScore = score([suggestion({ rules: [{ kind: "meter", meterKey: "cycles", interval: 1 }] })]);
    const bad = summarize([badScore], [], new Map());
    expect(bad.gates).toMatchObject({ foreignMeterKeys: 1, passed: false });

    const report = renderReport({ fast: "f", strong: "s" }, bad, [badScore]);
    expect(report).toContain("## Hard gates: FAIL");
    expect(report).toContain("foreignMeterKeys: Replace spark plugs: cycles");
  });
});

describe("checkExpected", () => {
  it("flags unknown documents and meters, bad rules and pages", () => {
    const withDoc: EvalCase = {
      ...CASE,
      request: {
        ...CASE.request,
        documents: [{ blobId: "blob-mm", name: "mm.pdf", mimeType: "application/pdf", sha256: "x", sizeBytes: 1 }],
      },
    };
    const problems = checkExpected(withDoc, {
      reviewed: false,
      tasks: [
        { titleAliases: ["ok"], rules: [{ kind: "meter", meterKey: "engine_hours", interval: 50 }], citations: [{ document: "blob-mm", pages: [3] }] },
        { titleAliases: ["meter"], rules: [{ kind: "meter", meterKey: "odometer", interval: 50 }] },
        { titleAliases: ["time"], rules: [], alternativeRules: [[{ kind: "time", every: 6, unit: "weeks" as "days" }]] },
        { titleAliases: ["doc"], rules: [], citations: [{ document: "blob-nope", pages: [0] }] },
        { titleAliases: ["far"], rules: [], citations: [{ regulation: "14 CFR § 91.413" }] },
        { titleAliases: ["blank"], rules: [], citations: [{ regulation: " " }] },
      ],
    });
    expect(problems).toEqual([
      expect.stringContaining("meter: bad rule"),
      expect.stringContaining("time: bad rule"),
      "doc: no document blob-nope in the case",
      "doc: bad pages [0]",
      "blank: empty regulation citation",
    ]);
  });
});
