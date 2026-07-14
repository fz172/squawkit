import { defineConfig } from "vitest/config";

// Tests live outside `src` so the deploy build (`tsc`, which only includes `src`) never
// compiles them. They run under the Firestore emulator via `npm test`
// (`firebase emulators:exec … vitest run`), which sets FIRESTORE_EMULATOR_HOST for us.
export default defineConfig({
  test: {
    include: ["test/**/*.test.ts"],
    environment: "node",
    testTimeout: 15000,
    hookTimeout: 30000,
    // All suites share one emulator project namespace, so run files sequentially — otherwise
    // one file's clearFirestore/wipe stomps another's seeded data mid-test.
    fileParallelism: false,
    // The storage-sweep config is required with no code-side default, so importing its triggers
    // throws unless it is set. Tests must therefore state it, exactly as a deploy must.
    env: {
      STORAGE_SWEEP_SCHEDULE: "every 24 hours",
      TOMBSTONE_RETENTION_DAYS: "30",
      ORPHAN_BLOB_GRACE_DAYS: "7",
      STORAGE_SWEEP_DRY_RUN: "true",
    },
  },
});
