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
    "TOMBSTONE_RETENTION_DAYS",
    "ORPHAN_BLOB_GRACE_DAYS",
    "STORAGE_SWEEP_DRY_RUN",
  ] as const;

  const VALID: Record<string, string> = {
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

  const READERS = {
    TOMBSTONE_RETENTION_DAYS: (e: Env) => e.tombstoneRetentionDays(),
    ORPHAN_BLOB_GRACE_DAYS: (e: Env) => e.orphanBlobGraceDays(),
    STORAGE_SWEEP_DRY_RUN: (e: Env) => e.storageSweepDryRun(),
  } as const;

  type Env = typeof import("../src/config/env.js");

  it("reads what .env states", async () => {
    setEnv({});
    const env = await import("../src/config/env.js");

    expect(env.tombstoneRetentionDays()).toBe(30);
    expect(env.orphanBlobGraceDays()).toBe(7);
    expect(env.storageSweepDryRun()).toBe(false);
  });

  for (const key of KEYS) {
    it(`throws rather than defaulting when ${key} is missing`, async () => {
      setEnv({ [key]: undefined });
      const env = await import("../src/config/env.js");
      expect(() => READERS[key](env)).toThrow(key);
    });
  }

  it("rejects a non-numeric retention window rather than falling back to one", async () => {
    setEnv({ TOMBSTONE_RETENTION_DAYS: "thirty" });
    const env = await import("../src/config/env.js");
    expect(() => env.tombstoneRetentionDays()).toThrow("TOMBSTONE_RETENTION_DAYS");
  });

  it("rejects a negative grace window", async () => {
    setEnv({ ORPHAN_BLOB_GRACE_DAYS: "-1" });
    const env = await import("../src/config/env.js");
    expect(() => env.orphanBlobGraceDays()).toThrow("ORPHAN_BLOB_GRACE_DAYS");
  });

  it("rejects anything but true/false for the dry-run switch", async () => {
    // A typo must not quietly arm a job that deletes users photos, nor quietly disarm one.
    setEnv({ STORAGE_SWEEP_DRY_RUN: "no" });
    const env = await import("../src/config/env.js");
    expect(() => env.storageSweepDryRun()).toThrow("STORAGE_SWEEP_DRY_RUN");
  });

  it("importing the module never throws: the deploy-time analysis has no .env", async () => {
    // The Firebase CLI does not pass user env into codebase analysis. Reading config at module scope
    // therefore fails every deploy with "missing variable" for a variable that is plainly set.
    setEnv({
      TOMBSTONE_RETENTION_DAYS: undefined,
      ORPHAN_BLOB_GRACE_DAYS: undefined,
      STORAGE_SWEEP_DRY_RUN: undefined,
    });
    await expect(import("../src/config/env.js")).resolves.toBeDefined();
  });
});
