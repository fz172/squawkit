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

// Subscription entitlement (SquawkIt Pro): server-authoritative at top-level subscriptions/{uid}.
// The owner may read it; nobody but the Admin SDK may write it — a client write would be a paywall
// bypass. See docs/subscription/subscription_design.html §3.
describe("subscriptions/{uid} rules", () => {
  it("lets the owner read an admin-written entitlement (round-trip)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "subscriptions/alice"), { status: 1, storageBytesUsed: 42 });
    });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(getDoc(doc(alice, "subscriptions/alice")));
  });

  it("denies the owner writing their own entitlement (no self-grant)", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, "subscriptions/alice"), { status: 1 }));
  });

  it("denies reading another user's entitlement", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "subscriptions/alice"), { status: 1 });
    });
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bob, "subscriptions/alice")));
  });

  it("denies unauthenticated access", async () => {
    const anon = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, "subscriptions/alice")));
  });
});

// Entitlement idempotency markers: pure server bookkeeping for the writer, functions-only. No client
// may read (it would leak billing-event ids) or write (it could forge a "already applied" and block
// a real grant). See docs/subscription/subscription_design.html §7.
describe("entitlement_ingest/{eventId} rules", () => {
  it("denies a signed-in user reading a marker", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "entitlement_ingest/evt-1"), { uid: "alice" });
    });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, "entitlement_ingest/evt-1")));
  });

  it("denies a signed-in user writing a marker", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(setDoc(doc(alice, "entitlement_ingest/evt-1"), { uid: "alice" }));
  });
});

// On-demand reconcile throttle (#355). Denying writes is what stops a client clearing its own
// marker and calling the reconcile endpoint in a loop against RevenueCat's rate limit.
describe("entitlement_reconcile/{uid} rules", () => {
  it("denies a user reading their own throttle marker", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "entitlement_reconcile/alice"), {
        lastReconciledAtMillis: 1,
      });
    });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, "entitlement_reconcile/alice")));
  });

  it("denies a user resetting their own throttle marker", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(
      setDoc(doc(alice, "entitlement_reconcile/alice"), { lastReconciledAtMillis: 0 }),
    );
  });
});

// N1 push token registry (notifications_design.md §7.1). No rule of its own: the own-tree rule
// already grants exactly "a user reads and writes only their own", and the default-deny catch-all
// covers everyone else. These cases exist to prove that reading is right — a token leaking to
// another account would let them be told what a stranger's mechanic is doing.
describe("users/{uid}/push_devices rules", () => {
  it("lets a user register and read their own device token", async () => {
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertSucceeds(
      setDoc(doc(alice, "users/alice/push_devices/install-1"), {
        token: "tok-a",
        platform: "android",
        appVersion: "1.0.0",
        enabled: true,
      }),
    );
    await assertSucceeds(getDoc(doc(alice, "users/alice/push_devices/install-1")));
  });

  it("denies reading another user's tokens", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users/alice/push_devices/install-1"), { token: "tok-a" });
    });
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(getDoc(doc(bob, "users/alice/push_devices/install-1")));
  });

  it("denies planting a token in another user's registry", async () => {
    // Not a theoretical hazard: a token written into someone else's tree would redirect that
    // account's notifications — squawk titles and collaborator names — to the attacker's device.
    const bob = testEnv.authenticatedContext("bob").firestore();
    await assertFails(
      setDoc(doc(bob, "users/alice/push_devices/install-evil"), { token: "tok-bob" }),
    );
  });
});

// The N1 counter and the hourly ceiling (§7.4). Both are function-only, and both matter: a client
// that could write the counter could forge a session start and overwrite another member's tray
// entry, and one that could write the rate document could raise its own cap.
describe("notification_activity and notification_rate rules", () => {
  it("denies a signed-in user reading or writing the activity counter", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "notification_activity/ac1__task__alice"), {
        writeCount: 1,
      });
    });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, "notification_activity/ac1__task__alice")));
    await assertFails(
      setDoc(doc(alice, "notification_activity/ac1__task__alice"), { writeCount: 0 }),
    );
  });

  it("denies a signed-in user reading or resetting the hourly send ceiling", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "notification_rate/ac1__2026082402"), { sendCount: 60 });
    });
    const alice = testEnv.authenticatedContext("alice").firestore();
    await assertFails(getDoc(doc(alice, "notification_rate/ac1__2026082402")));
    await assertFails(setDoc(doc(alice, "notification_rate/ac1__2026082402"), { sendCount: 0 }));
  });
});
