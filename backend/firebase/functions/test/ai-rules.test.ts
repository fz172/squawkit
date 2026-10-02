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
  AI_JOB_INPUTS_COLLECTION,
  AI_JOBS_COLLECTION,
  AI_SPEND_COLLECTION,
  AI_USAGE_COLLECTION,
  aiJobDocPath,
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

function job(callerUid: string, thingId = "t1") {
  return { kind: 1, callerUid, hostUid: "host", thingId, status: 1, createdAt: new Date() };
}

async function seed(path: string, data: Record<string, unknown>): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), path), data);
  });
}

describe("ai_jobs/{jobId} rules", () => {
  it("lets the caller read their own job", async () => {
    await seed(aiJobDocPath("j1"), job("alice"));
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(alice, aiJobDocPath("j1"))));
  });

  it("denies another user, a share member included", async () => {
    await seed(aiJobDocPath("j1"), job("alice"));
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bob, aiJobDocPath("j1"))));
  });

  it("denies a signed-out reader", async () => {
    await seed(aiJobDocPath("j1"), job("alice"));
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, aiJobDocPath("j1"))));
  });

  it("reads a closed or expired job as absent, not as a denial", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    const snap = await assertSucceeds(getDoc(doc(alice, aiJobDocPath("gone"))));
    expect(snap.exists()).toBe(false);
  });

  it("lets the caller list their jobs for a Thing when the query filters on callerUid", async () => {
    await seed(aiJobDocPath("j1"), job("alice"));
    await seed(aiJobDocPath("j2"), job("bob"));
    const alice = testEnv.authenticatedContext("alice").firestore();
    const snap = await assertSucceeds(
      getDocs(
        query(
          collection(alice, AI_JOBS_COLLECTION),
          where("callerUid", "==", "alice"),
          where("thingId", "==", "t1"),
        ),
      ),
    );
    expect(snap.docs.map((d) => d.id)).toEqual(["j1"]);
  });

  it("denies a list that does not filter on the caller", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDocs(collection(alice, AI_JOBS_COLLECTION)));
    await assertFails(
      getDocs(query(collection(alice, AI_JOBS_COLLECTION), where("callerUid", "==", "bob"))),
    );
  });

  it("denies the caller creating, updating or deleting a job", async () => {
    await seed(aiJobDocPath("j1"), job("alice"));
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, aiJobDocPath("j2")), job("alice")));
    await assertFails(updateDoc(doc(alice, aiJobDocPath("j1")), { status: 3 }));
    await assertFails(deleteDoc(doc(alice, aiJobDocPath("j1"))));
  });
});

describe("functions-only ai_* collections", () => {
  const paths = [
    `${AI_JOB_INPUTS_COLLECTION}/j1`,
    `${AI_USAGE_COLLECTION}/alice_t1`,
    `${AI_SPEND_COLLECTION}/202610`,
    `${AI_COST_LOG_COLLECTION}/r1`,
    `${AI_CACHE_COLLECTION}/doc:abc:tasks-4`,
    AI_CONFIG_DOC_PATH,
  ];

  for (const path of paths) {
    it(`denies a signed-in user reading or writing ${path}`, async () => {
      await seed(path, { callerUid: "alice" });
      const alice = testEnv.authenticatedContext("alice").firestore();
      await assertFails(getDoc(doc(alice, path)));
      await assertFails(setDoc(doc(alice, path), { enabled: true }));
      await assertFails(deleteDoc(doc(alice, path)));
    });
  }

  it("denies listing the cost log and the cache", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDocs(collection(alice, AI_COST_LOG_COLLECTION)));
    await assertFails(getDocs(collection(alice, AI_CACHE_COLLECTION)));
  });
});
