import { describe, expect, it } from "vitest";

import {
  DEFAULT_AI_CONFIG,
  aiJobDocPath,
  aiJobInputDocPath,
  aiSpendMonthKey,
  aiUsageDocPath,
  parseAiConfig,
} from "../../src/ai/collections.js";

describe("ai collection paths", () => {
  it("keeps a job and its input under the caller", () => {
    expect(aiJobDocPath("alice", "j1")).toBe("ai_jobs/alice/job/j1");
    expect(aiJobInputDocPath("alice", "j1")).toBe("ai_job_inputs/alice/input/j1");
  });

  it("keys usage by the Thing's tree, since a Thing id is unique only within one", () => {
    expect(aiUsageDocPath("host", "t1")).toBe("ai_usage/host/thing/t1");
  });

  it("keys spend by UTC month", () => {
    expect(aiSpendMonthKey(new Date("2026-10-31T23:30:00-07:00"))).toBe("202611");
    expect(aiSpendMonthKey(new Date("2026-01-05T00:00:00Z"))).toBe("202601");
  });
});

describe("parseAiConfig", () => {
  it("reads a well-formed config as written", () => {
    const live = { ...DEFAULT_AI_CONFIG, enabled: true, maxDocumentsPerRun: 3 };
    expect(parseAiConfig(live)).toEqual(live);
  });

  it("seeds disabled", () => {
    expect(DEFAULT_AI_CONFIG.enabled).toBe(false);
  });

  it.each([
    ["a missing document", undefined],
    ["a non-object", "on"],
    ["a string switch", { ...DEFAULT_AI_CONFIG, enabled: "true" }],
    ["a missing provider", { ...DEFAULT_AI_CONFIG, enabled: true, fastProvider: "" }],
    ["no ceilings", { ...DEFAULT_AI_CONFIG, enabled: true, monthlyCeilingMicros: undefined }],
    [
      "a negative ceiling",
      { ...DEFAULT_AI_CONFIG, enabled: true, monthlyCeilingMicros: { free: -1, pro: 1, total: 1 } },
    ],
    ["no document cap", { ...DEFAULT_AI_CONFIG, enabled: true, maxDocumentsPerRun: undefined }],
  ])("fails closed on %s", (_name, data) => {
    expect(parseAiConfig(data).enabled).toBe(false);
  });
});
