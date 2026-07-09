import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

// firestore.rules lives at backend/firebase/firestore.rules — two levels up from this test.
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

// Smoke test for the harness itself: exercises the baseline rules end to end so a green run
// proves the emulator, rules file, and rules-unit-testing wiring all work. The comprehensive
// sharing matrix lands in #112 once #111 adds the aircraft_shares rules.
describe("baseline firestore.rules", () => {
  it("lets a signed-in user read and write their own users/{uid} subtree", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(setDoc(doc(alice, "users/alice/aircraft/ac1"), { tail: "N123" }));
    await assertSucceeds(getDoc(doc(alice, "users/alice/aircraft/ac1")));
  });

  it("denies reading or writing another user's subtree", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users/alice/aircraft/ac1"), { tail: "N123" });
    });
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bob, "users/alice/aircraft/ac1")));
    await assertFails(setDoc(doc(bob, "users/alice/aircraft/ac1"), { tail: "hijacked" }));
  });

  it("denies unauthenticated access", async () => {
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, "users/alice/aircraft/ac1")));
  });

  it("default-denies paths outside the users tree (e.g. aircraft_shares, pre-#111)", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, "aircraft_shares/ac1")));
  });
});
