import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The storage-sweep config has no code-side default (#159).
 *
 * A silent fallback would let the deployed sweep run with a retention window nobody chose — .env
 * saying one thing, the function doing another — and the first sign of the mismatch would be data
 * that is already gone. Config it cannot read is config it must not act on.
 */
describe("storage sweep config", () => {
  const KEYS = [
    "STORAGE_SWEEP_SCHEDULE",
    "TOMBSTONE_RETENTION_DAYS",
    "ORPHAN_BLOB_GRACE_DAYS",
    "STORAGE_SWEEP_DRY_RUN",
  ] as const;

  const VALID: Record<string, string> = {
    STORAGE_SWEEP_SCHEDULE: "every 24 hours",
    TOMBSTONE_RETENTION_DAYS: "30",
    ORPHAN_BLOB_GRACE_DAYS: "7",
    STORAGE_SWEEP_DRY_RUN: "false",
  };

  const saved: Record<string, string | undefined> = {};

  beforeEach(() => {
    vi.resetModules();
    for (const key of KEYS) saved[key] = process.env[key];
  });

  afterEach(() => {
    for (const key of KEYS) {
      if (saved[key] == null) delete process.env[key];
      else process.env[key] = saved[key];
    }
  });

  function setEnv(overrides: Record<string, string | undefined>): void {
    for (const key of KEYS) {
      const value = key in overrides ? overrides[key] : VALID[key];
      if (value == null) delete process.env[key];
      else process.env[key] = value;
    }
  }

  it("loads what .env states", async () => {
    setEnv({});
    const env = await import("../src/config/env.js");

    expect(env.STORAGE_SWEEP_SCHEDULE).toBe("every 24 hours");
    expect(env.TOMBSTONE_RETENTION_DAYS).toBe(30);
    expect(env.ORPHAN_BLOB_GRACE_DAYS).toBe(7);
    expect(env.STORAGE_SWEEP_DRY_RUN).toBe(false);
  });

  for (const key of KEYS) {
    it(`refuses to load when ${key} is missing`, async () => {
      setEnv({ [key]: undefined });
      await expect(import("../src/config/env.js")).rejects.toThrow(key);
    });
  }

  it("rejects a non-numeric retention window rather than falling back to one", async () => {
    setEnv({ TOMBSTONE_RETENTION_DAYS: "thirty" });
    await expect(import("../src/config/env.js")).rejects.toThrow("TOMBSTONE_RETENTION_DAYS");
  });

  it("rejects a negative grace window", async () => {
    setEnv({ ORPHAN_BLOB_GRACE_DAYS: "-1" });
    await expect(import("../src/config/env.js")).rejects.toThrow("ORPHAN_BLOB_GRACE_DAYS");
  });

  it("rejects anything but true/false for the dry-run switch", async () => {
    // A typo must not quietly arm a job that deletes users' photos — nor quietly disarm one.
    setEnv({ STORAGE_SWEEP_DRY_RUN: "no" });
    await expect(import("../src/config/env.js")).rejects.toThrow("STORAGE_SWEEP_DRY_RUN");
  });
});
