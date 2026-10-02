import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";

import {
  AI_CACHE_COLLECTION,
  AI_CONFIG_DOC_PATH,
  AI_COST_LOG_COLLECTION,
  AI_SPEND_COLLECTION,
  aiCacheDocPath,
  aiJobDocPath,
  aiJobInputDocPath,
  aiJobsCollectionPath,
  aiUsageDocPath,
} from "../src/ai/collections.js";

// The AI backend's collections (docs/ai/task_population_design.md §4.3): a job is readable by the
// user who started it and nobody else, and nothing in any ai_* collection is client-writable.

const rulesPath = resolve(dirname(fileURLToPath(import.meta.url)), "../../firestore.rules");

let testEnv: RulesTestEnvironment;

beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-squawkit",
    firestore: { rules: readFileSync(rulesPath, "utf8") },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function job(thingId = "t1") {
  return { kind: 1, hostUid: "host", thingId, status: 1, createdAt: new Date() };
}

async function seed(path: string, data: Record<string, unknown>): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path), data);
  });
}

describe("ai_jobs/{callerUid}/job/{jobId} rules", () => {
  it("lets the caller read their own job", async () => {
    await seed(aiJobDocPath("alice", "j1"), job());
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(alice, aiJobDocPath("alice", "j1"))));
  });

  it("denies another user, a share member included", async () => {
    await seed(aiJobDocPath("alice", "j1"), job());
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bob, aiJobDocPath("alice", "j1"))));
  });

  it("denies a signed-out reader", async () => {
    await seed(aiJobDocPath("alice", "j1"), job());
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, aiJobDocPath("alice", "j1"))));
  });

  it("reads a closed or expired job as absent, not as a denial", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const snap = await assertSucceeds(getDoc(doc(alice, aiJobDocPath("alice", "gone"))));
    expect(snap.exists()).toBe(false);
  });

  it("lets the caller list their jobs for a Thing with no caller filter", async () => {
    await seed(aiJobDocPath("alice", "j1"), job("t1"));
    await seed(aiJobDocPath("alice", "j2"), job("t2"));
    await seed(aiJobDocPath("bob", "j3"), job("t1"));
    const alice = testEnv.authenticatedContext("alice").firestore();
    const snap = await assertSucceeds(
      getDocs(query(collection(alice, aiJobsCollectionPath("alice")), where("thingId", "==", "t1"))),
    );
    expect(snap.docs.map((d) => d.id)).toEqual(["j1"]);
  });

  it("denies listing another user's jobs", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDocs(collection(alice, aiJobsCollectionPath("bob"))));
  });

  it("denies the caller their own job's input, which holds the Thing's logs", async () => {
    await seed(aiJobDocPath("alice", "j1"), job());
    await seed(aiJobInputDocPath("alice", "j1"), { request: "base64" });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, aiJobInputDocPath("alice", "j1"))));
    await assertFails(setDoc(doc(alice, aiJobInputDocPath("alice", "j1")), { request: "x" }));
    await assertFails(getDocs(collection(alice, `${aiJobDocPath("alice", "j1")}/input`)));
  });

  it("denies the caller creating, updating or deleting a job", async () => {
    await seed(aiJobDocPath("alice", "j1"), job());
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, aiJobDocPath("alice", "j2")), job()));
    await assertFails(updateDoc(doc(alice, aiJobDocPath("alice", "j1")), { status: 3 }));
    await assertFails(deleteDoc(doc(alice, aiJobDocPath("alice", "j1"))));
  });
});

describe("functions-only ai_* collections", () => {
  const paths = [
    aiJobInputDocPath("alice", "j1"),
    aiUsageDocPath("alice", "t1"),
    `${AI_SPEND_COLLECTION}/202610`,
    `${AI_COST_LOG_COLLECTION}/r1`,
    aiCacheDocPath("doc/tasks-4/abc"),
    AI_CONFIG_DOC_PATH,
  ];

  for (const path of paths) {
    it(`denies a signed-in user reading or writing ${path}`, async () => {
      await seed(path, { hostUid: "alice" });
      const alice = testEnv.authenticatedContext("alice").firestore();
      await assertFails(getDoc(doc(alice, path)));
      await assertFails(setDoc(doc(alice, path), { enabled: true }));
      await assertFails(deleteDoc(doc(alice, path)));
    });
  }

  it("denies listing the cost log and the cache", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDocs(collection(alice, AI_COST_LOG_COLLECTION)));
    await assertFails(getDocs(collection(alice, `${AI_CACHE_COLLECTION}/doc/tasks-4`)));
  });
});
