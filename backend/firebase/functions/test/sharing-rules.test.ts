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
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const rulesPath = resolve(dirname(fileURLToPath(import.meta.url)), "../../firestore.rules");

const HOST = "host-uid";
const OWNER2 = "owner2-uid";
const TECH = "tech-uid";
const STRANGER = "stranger-uid";
const AC = "ac-shared";
const LOG = "log-1";

// MIGRATION (task F3/F4): the entity tree is `/thing/` now — Phase F2 deleted the `/aircraft/`
// documents and F3 removed the rules block that governed them. `legacyThingDoc` survives only so
// F4 can assert that path is genuinely dead rather than merely unused.
const thingDoc = `users/${HOST}/thing/${AC}`;
const logDoc = `${thingDoc}/maintenance_log/${LOG}`;
const legacyThingDoc = `users/${HOST}/aircraft/${AC}`;
// MIGRATION (Phase G3): the ACL tree has moved. `shareRole()` now resolves against thing_shares,
// so this is where every fixture below has to seed — the aircraft_shares match block is still in
// the rules file until G6, but nothing authorizes against it any more.
const shareDoc = `thing_shares/${HOST}/thing/${AC}`;
/** The retired tree. Still matched by the rules, but no longer consulted for membership. */
const legacyShareDoc = `thing_shares/${HOST}/thing/${AC}`;

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

// Seed the shared aircraft, its ACL, a member doc, and a log — all bypassing rules.
beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, thingDoc), { registration: "N123", writerUid: HOST });
    await setDoc(doc(db, logDoc), { note: "oil change", writerUid: HOST });
    await setDoc(doc(db, shareDoc), {
      hostUid: HOST,
      thingId: AC,
      memberRoles: { [HOST]: "owner", [OWNER2]: "owner", [TECH]: "technician" },
    });
    await setDoc(doc(db, `${shareDoc}/members/${TECH}`), {
      role: "technician",
      displayName: "Tech",
      invitedBy: HOST,
    });
    await setDoc(doc(db, `${shareDoc}/members/${OWNER2}`), {
      role: "owner",
      displayName: "Owner Two",
      invitedBy: HOST,
    });
  });
});

const as = (uid: string | null) =>
  (uid ? testEnv.authenticatedContext(uid) : testEnv.unauthenticatedContext()).firestore();

describe("shared aircraft document", () => {
  it("member may GET the shared aircraft doc", async () => {
    await assertSucceeds(getDoc(doc(as(TECH), thingDoc)));
  });

  it("non-member may NOT get the shared aircraft doc", async () => {
    await assertFails(getDoc(doc(as(STRANGER), thingDoc)));
  });

  it("member may NOT LIST the host's aircraft collection (keeps other aircraft private)", async () => {
    await assertFails(getDocs(collection(as(TECH), `users/${HOST}/aircraft`)));
  });

  it("co-owner may edit the aircraft doc (attested, non-delete)", async () => {
    await assertSucceeds(
      setDoc(doc(as(OWNER2), thingDoc), { registration: "N999", deleted: false, writerUid: OWNER2 }),
    );
  });

  it("technician may NOT write the aircraft doc", async () => {
    await assertFails(
      setDoc(doc(as(TECH), thingDoc), { registration: "N999", writerUid: TECH }),
    );
  });

  it("co-owner may NOT forge writerUid on the aircraft doc", async () => {
    await assertFails(
      setDoc(doc(as(OWNER2), thingDoc), { registration: "N999", writerUid: HOST }),
    );
  });

  it("hosting owner may delete (tombstone) the aircraft", async () => {
    await assertSucceeds(
      setDoc(doc(as(HOST), thingDoc), { registration: "N123", deleted: true, writerUid: HOST }),
    );
  });

  it("co-owner may NOT delete (tombstone) the aircraft — hosting owner only", async () => {
    await assertFails(
      setDoc(doc(as(OWNER2), thingDoc), { registration: "N123", deleted: true, writerUid: OWNER2 }),
    );
  });
});

describe("nested maintenance data", () => {
  it("member may read a log", async () => {
    await assertSucceeds(getDoc(doc(as(TECH), logDoc)));
  });

  it("member may LIST the nested maintenance_log collection", async () => {
    await assertSucceeds(getDocs(collection(as(TECH), `${thingDoc}/maintenance_log`)));
  });

  it("member may write a log (attested)", async () => {
    await assertSucceeds(
      setDoc(doc(as(TECH), logDoc), { note: "fixed", writerUid: TECH }),
    );
  });

  it("member may NOT forge writerUid on a log", async () => {
    await assertFails(
      setDoc(doc(as(TECH), logDoc), { note: "fixed", writerUid: OWNER2 }),
    );
  });

  it("non-member may NOT read or write nested data", async () => {
    await assertFails(getDoc(doc(as(STRANGER), logDoc)));
    await assertFails(setDoc(doc(as(STRANGER), logDoc), { note: "x", writerUid: STRANGER }));
  });

  it("member may write a comment (attested)", async () => {
    // The point of the comments feature: a technician leaving a note on the host's squawk. Without
    // `comment` in isSharedAircraftKind this fails and the thread is read-only for everyone but
    // the host.
    await assertSucceeds(
      setDoc(doc(as(TECH), `${thingDoc}/comment/c1`), {
        payload: "x",
        writerUid: TECH,
      }),
    );
  });

  it("member may NOT forge writerUid on a comment", async () => {
    await assertFails(
      setDoc(doc(as(TECH), `${thingDoc}/comment/c1`), {
        payload: "x",
        writerUid: OWNER2,
      }),
    );
  });

  it("member may NOT write an unknown kind into the host's subtree", async () => {
    await assertFails(
      setDoc(doc(as(TECH), `${thingDoc}/evil/x`), { data: 1, writerUid: TECH }),
    );
  });
});

describe("aircraft_shares ACL root", () => {
  it("member may get the share doc", async () => {
    await assertSucceeds(getDoc(doc(as(TECH), shareDoc)));
  });

  it("non-member may NOT get the share doc", async () => {
    await assertFails(getDoc(doc(as(STRANGER), shareDoc)));
  });

  it("NOBODY creates a share from a client — the invite callable bootstraps it (#164)", async () => {
    // Even the genuine host is denied: createThingShareInvite does this as admin. That retires
    // the last forgeable check, which leaned on "I have an aircraft with this id in my tree".
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${HOST}/aircraft/ac-new`), { registration: "N2" });
    });
    await assertFails(
      setDoc(doc(as(HOST), `thing_shares/${HOST}/thing/ac-new`), {
        hostUid: HOST,
        thingId: "ac-new",
        memberRoles: { [HOST]: "owner" },
      }),
    );
  });

  it("cannot create a share for an aircraft you don't own (spoof)", async () => {
    await assertFails(
      setDoc(doc(as(STRANGER), `thing_shares/${STRANGER}/thing/ac-new`), {
        hostUid: STRANGER,
        thingId: "ac-new",
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("cannot create a share claiming a different hostUid (spoof)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${STRANGER}/aircraft/ac-new`), { registration: "N2" });
    });
    await assertFails(
      setDoc(doc(as(STRANGER), `thing_shares/${STRANGER}/thing/ac-new`), {
        hostUid: HOST,
        thingId: "ac-new",
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("no client may update or delete the share root (function-managed)", async () => {
    await assertFails(updateDoc(doc(as(HOST), shareDoc), { memberRoles: { [HOST]: "owner" } }));
    await assertFails(deleteDoc(doc(as(HOST), shareDoc)));
  });
});

// First share of an aircraft: no aircraft_shares doc exists yet. The owner must be able to GET the
// missing ACL doc and bootstrap it before the first invite (docs/sharing §3.1). Regression guard for
// the get-rule ordering: the own-aircraft existence check must be evaluated before isShareMember,
// whose get() of the missing doc would otherwise error and deny the whole rule.
// #202: the ACL read rule used `exists(users/{me}/aircraft/{acId})` to mean "I am the host". But an
// aircraft id is only unique WITHIN a tree, and anyone may write anything into their own tree — so a
// stranger who knows an acId (it is in every invite link, and every ex-member still remembers it)
// could fabricate a same-id aircraft and read the victim's ACL, roster, and technician mirrors.
describe("aircraft_shares — a fabricated same-id aircraft grants nothing (#202)", () => {
  // The attacker knows AC (from an invite link, or from having once been a member) and plants an
  // aircraft with that id in their OWN tree. That is a legal write: it is their tree.
  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${STRANGER}/aircraft/${AC}`), {
        registration: "FAKE",
      });
    });
  });

  it("may NOT get the ACL root (hostUid + every member uid and role)", async () => {
    await assertFails(getDoc(doc(as(STRANGER), shareDoc)));
  });

  it("may NOT read the member roster (names, photos, technician certificate numbers)", async () => {
    await assertFails(getDoc(doc(as(STRANGER), `${shareDoc}/members/${TECH}`)));
  });

  it("may NOT list the member roster", async () => {
    await assertFails(getDocs(collection(as(STRANGER), `${shareDoc}/members`)));
  });

  it("may NOT read pending invites", async () => {
    await assertFails(getDocs(collection(as(STRANGER), `${shareDoc}/invites`)));
  });

  it("still may NOT read the victim's aircraft or its records", async () => {
    // The escalation this protects against: fabricating the aircraft must not make you a member.
    await assertFails(getDoc(doc(as(STRANGER), thingDoc)));
    await assertFails(getDoc(doc(as(STRANGER), logDoc)));
  });
});

// #204: the ACL is keyed under the HOST, so a share a caller can create only ever governs the
// caller's own tree. Planting a same-id aircraft still works — it is their tree — but the share it
// buys them is over their own aircraft. Harmless.
// #164: the code IS the secret. The doc holds the aircraft id, host uid and role, keyed by the code
// — so a client that could read it would get back exactly what the pairing code exists to withhold.
describe("invite_codes — clients cannot touch them (#164)", () => {
  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "invite_codes/ABCD2345"), {
        hostUid: HOST,
        thingId: AC,
        role: "technician",
      });
      await setDoc(doc(ctx.firestore(), `invite_attempts/${TECH}`), { failures: 3 });
    });
  });

  it("nobody may read a code — not a stranger, not a member, not the host who made it", async () => {
    await assertFails(getDoc(doc(as(STRANGER), "invite_codes/ABCD2345")));
    await assertFails(getDoc(doc(as(TECH), "invite_codes/ABCD2345")));
    await assertFails(getDoc(doc(as(HOST), "invite_codes/ABCD2345")));
  });

  it("nobody may list them (no fishing for live codes)", async () => {
    await assertFails(getDocs(collection(as(HOST), "invite_codes")));
  });

  it("nobody may forge one", async () => {
    await assertFails(
      setDoc(doc(as(STRANGER), "invite_codes/EVIL2345"), {
        hostUid: HOST,
        thingId: AC,
        role: "owner",
      }),
    );
  });

  it("nobody may read or reset their rate-limit budget", async () => {
    // Readable → the attacker learns how much guessing they have left. Writable → they reset it.
    await assertFails(getDoc(doc(as(TECH), `invite_attempts/${TECH}`)));
    await assertFails(setDoc(doc(as(TECH), `invite_attempts/${TECH}`), { failures: 0 }));
  });
});

describe("aircraft_shares — namespaced by host (#204)", () => {
  beforeEach(async () => {
    // The attacker plants an aircraft carrying the victim's id in their OWN tree. Legal: own tree.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${STRANGER}/aircraft/${AC}`), { registration: "FAKE" });
    });
  });

  it("may NOT create a share in the host's namespace, even holding a same-id aircraft", async () => {
    // The direct question: can a malicious client just spoof the host segment? No — the rules pin it
    // to request.auth.uid, which comes from a signed token.
    await assertFails(
      setDoc(doc(as(STRANGER), `thing_shares/${HOST}/thing/${AC}`), {
        hostUid: STRANGER,
        thingId: AC,
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("may NOT create it while also claiming to be the host", async () => {
    await assertFails(
      setDoc(doc(as(STRANGER), `thing_shares/${HOST}/thing/${AC}`), {
        hostUid: HOST,
        thingId: AC,
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("cannot create a share at all now — and still reaches nothing of the victim's", async () => {
    // Since #164 client ACL creation is closed outright, so the fabrication buys not even a share
    // over their own plane. Belt and braces on top of #204's namespacing.
    await assertFails(
      setDoc(doc(as(STRANGER), `thing_shares/${STRANGER}/thing/${AC}`), {
        hostUid: STRANGER,
        thingId: AC,
        memberRoles: { [STRANGER]: "owner" },
      }),
    );

    // The victim's aircraft and records remain out of reach: the rules resolve the ACL from the tree
    // being READ (users/{HOST}/...), not from an id the attacker controls.
    await assertFails(getDoc(doc(as(STRANGER), thingDoc)));
    await assertFails(getDoc(doc(as(STRANGER), logDoc)));
    await assertFails(getDoc(doc(as(STRANGER), shareDoc)));
  });

  it("an ex-member cannot re-claim a share whose ACL was torn down by a delete", async () => {
    // The reachable path from #204: deleting a shared aircraft removes its ACL while leaving the
    // records behind (tombstoned, payloads intact). An ex-member knows the id forever. Under a
    // global key they could re-claim the abandoned slot and read the deleted aircraft.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), shareDoc)); // the teardown the delete trigger performs
    });

    // TECH (revoked / ex-member) plants the same-id aircraft and tries to claim the empty slot.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${TECH}/aircraft/${AC}`), { registration: "FAKE" });
    });
    await assertFails(
      setDoc(doc(as(TECH), `thing_shares/${HOST}/thing/${AC}`), {
        hostUid: TECH,
        thingId: AC,
        memberRoles: { [TECH]: "owner" },
      }),
    );

    // And the victim's leftover records stay unreadable.
    await assertFails(getDoc(doc(as(TECH), thingDoc)));
    await assertFails(getDoc(doc(as(TECH), logDoc)));
  });
});

describe("aircraft_shares ACL root — first share (no ACL doc yet)", () => {
  const FRESH = "ac-fresh";
  const freshShare = `thing_shares/${HOST}/thing/${FRESH}`;

  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${HOST}/aircraft/${FRESH}`), {
        registration: "N9",
        writerUid: HOST,
      });
    });
  });

  // Before the first invite there is no ACL — and nothing about it is readable or writable from a
  // client, by anyone, including the owner (#202 + #164). There is nothing to read: the roster is
  // empty and the ACL does not exist. And there is nothing to write: createThingShareInvite
  // bootstraps it server-side.
  //
  // Denying is safe because the client expects it. Manage Access maps the denial to
  // AircraftShareState.accessDenied, shows an empty roster, and still offers Invite — the owner's
  // role comes from the local refs, not from this doc. Covered by ManageAccessViewModelTest.
  it("nobody reads the missing ACL doc — not even the owner (nothing to read)", async () => {
    await assertFails(getDoc(doc(as(HOST), freshShare)));
    await assertFails(getDoc(doc(as(STRANGER), freshShare)));
  });

  it("nobody reads the roster or invite list before a share exists", async () => {
    await assertFails(getDocs(collection(as(HOST), `${freshShare}/members`)));
    await assertFails(getDocs(collection(as(HOST), `${freshShare}/invites`)));
  });

  it("nobody bootstraps the ACL or writes an invite from a client (#164)", async () => {
    await assertFails(
      setDoc(doc(as(HOST), freshShare), {
        hostUid: HOST,
        thingId: FRESH,
        memberRoles: { [HOST]: "owner" },
      }),
    );
    await assertFails(
      setDoc(doc(as(HOST), `${freshShare}/invites/code-id-1`), {
        role: "technician",
        createdBy: HOST,
      }),
    );
  });

  it("a stranger may NOT read the members or invites lists", async () => {
    await assertFails(getDocs(collection(as(STRANGER), `${freshShare}/members`)));
    await assertFails(getDocs(collection(as(STRANGER), `${freshShare}/invites`)));
  });
});

describe("member documents", () => {
  it("member may read another member's doc", async () => {
    await assertSucceeds(getDoc(doc(as(OWNER2), `${shareDoc}/members/${TECH}`)));
  });

  // The Manage Access roster is a LIST of this collection, not a get — and it must work for a
  // technician, since the roster is what shows them the owner and backs their Leave action.
  // Invites stay owner-only (see the "invites" suite), so the client must not couple the two.
  it("technician may LIST the members collection (backs their Manage Access roster)", async () => {
    await assertSucceeds(getDocs(collection(as(TECH), `${shareDoc}/members`)));
  });

  it("member may update their own display, keeping role unchanged", async () => {
    await assertSucceeds(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${TECH}`), { displayName: "New Name" }),
    );
  });

  // The member's own client publishes the mirror (design §7.1) — no proto decoding server-side.
  it("member may publish their own technician mirror", async () => {
    await assertSucceeds(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${TECH}`), {
        displayName: "Sponge Bob",
        photoUrl: "https://example.com/p.jpg",
        technicianMirror: {
          name: "Sponge Bob",
          certificateType: "CERTIFICATE_TYPE_AMT",
          certNumber: "A&P-123",
          certExpireLimit: "CERT_EXPIRE_LIMIT_NEVER_EXPIRES",
        },
      }),
    );
  });

  it("member may NOT publish a mirror onto another member's doc", async () => {
    await assertFails(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${HOST}`), {
        technicianMirror: { name: "Impostor" },
      }),
    );
  });

  it("member may NOT change their own role (immutable from clients)", async () => {
    await assertFails(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${TECH}`), { role: "owner" }),
    );
  });

  it("member may NOT update another member's doc", async () => {
    await assertFails(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${OWNER2}`), { displayName: "hijack" }),
    );
  });

  // The hosting owner never redeems, so no function ever writes their member doc — they self-create
  // it, which is what makes them appear in the roster at all (design §7.2).
  it("host may create their OWN member doc at the role the ACL grants them", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), `${shareDoc}/members/${HOST}`));
    });
    await assertSucceeds(
      setDoc(doc(as(HOST), `${shareDoc}/members/${HOST}`), {
        role: "owner",
        displayName: "Sponge Bob",
        photoUrl: "https://example.com/p.jpg",
      }),
    );
  });

  it("member may NOT self-create at a role the ACL does not grant them (escalation)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), `${shareDoc}/members/${TECH}`));
    });
    await assertFails(
      setDoc(doc(as(TECH), `${shareDoc}/members/${TECH}`), {
        role: "owner",
        displayName: "Sneaky",
      }),
    );
  });

  it("a stranger may NOT self-create a member doc (mint membership)", async () => {
    await assertFails(
      setDoc(doc(as(STRANGER), `${shareDoc}/members/${STRANGER}`), {
        role: "technician",
        displayName: "Nobody",
      }),
    );
  });

  // Self-create is allowed (above); creating a doc for SOMEONE ELSE, and any delete, stay
  // function-only — that is what keeps membership itself function-managed.
  it("no client may create a member doc for another uid, or delete one", async () => {
    await assertFails(
      setDoc(doc(as(HOST), `${shareDoc}/members/new-uid`), { role: "technician" }),
    );
    await assertFails(deleteDoc(doc(as(HOST), `${shareDoc}/members/${TECH}`)));
  });
});

describe("invites", () => {
  const inviteDoc = `${shareDoc}/invites/token-hash-1`;

  it("owner READS the pending-invite record, but cannot write one (#164)", async () => {
    // The record carries role + expiry so the owner can list and cancel. The code itself is not in
    // it, and creating/cancelling goes through the callables — both must touch invite_codes, which
    // no client can see.
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), inviteDoc), { role: "technician", createdBy: HOST });
    });
    await assertSucceeds(getDoc(doc(as(OWNER2), inviteDoc)));
    await assertFails(setDoc(doc(as(OWNER2), inviteDoc), { role: "owner", createdBy: OWNER2 }));
  });

  it("technician may NOT create or read invites", async () => {
    await assertFails(
      setDoc(doc(as(TECH), inviteDoc), { role: "technician", createdBy: TECH }),
    );
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), inviteDoc), { role: "technician", createdBy: HOST });
    });
    await assertFails(getDoc(doc(as(TECH), inviteDoc)));
  });

  it("no client may delete an invite", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), inviteDoc), { role: "technician", createdBy: HOST });
    });
    await assertFails(deleteDoc(doc(as(HOST), inviteDoc)));
  });
});

// --- MIGRATION: the /thing/ and thing_shares blocks (tasks B7, B8, B10) ---------------------------
//
// docs/product/thing_migration_design.md §2.4a and §2.9. `firestore.rules` deploys as ONE atomic
// unit, so the new blocks are added ALONGSIDE the `/aircraft/` and `aircraft_shares` ones rather
// than replacing them, and every assertion above must keep passing for the whole migration window.
// These are the parallel assertions for the new shapes.
//
// The intermediate state this pins down is easy to get wrong: the ENTITY tree moves in Phase D, but
// the ACL tree does not move until Phase G. So a `/thing/` document is authorized by an ACL that is
// now at `thing_shares` as of G3. Both entity blocks resolve membership through the same helper, so
// these assertions cover the /thing/ tree specifically rather than the ACL behind it.
describe("migration: the retired /aircraft/ entity path is dead (task F4)", () => {
  // F3 removed `match /aircraft/{acId}`, so what used to be a member's read is now denied by the
  // default-deny catch-all. Asserting that explicitly is the point of F4: without it, "the block is
  // gone" and "the block is still there but nothing uses it" look identical from the test suite,
  // and only one of those is true.
  //
  // The host is deliberately NOT tested here. `match /users/{userId}/{document=**}` still grants an
  // owner their whole subtree at any path, so the host would pass and prove nothing about the
  // removed block. Sharing is the capability that block uniquely granted.
  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), legacyThingDoc), { registration: "N123" });
      await setDoc(doc(ctx.firestore(), `${legacyThingDoc}/maintenance_log/${LOG}`), {
        note: "old",
        writerUid: HOST,
      });
    });
  });

  it("denies a member the retired aircraft doc", async () => {
    await assertFails(getDoc(doc(as(TECH), legacyThingDoc)));
  });

  it("denies a member its nested records, read and write alike", async () => {
    await assertFails(getDoc(doc(as(TECH), `${legacyThingDoc}/maintenance_log/${LOG}`)));
    await assertFails(
      setDoc(doc(as(TECH), `${legacyThingDoc}/maintenance_log/${LOG}`), {
        note: "x",
        writerUid: TECH,
      }),
    );
  });

  it("denies a co-owner the write the removed block used to allow", async () => {
    await assertFails(
      setDoc(doc(as(OWNER2), legacyThingDoc), {
        registration: "N999",
        deleted: false,
        writerUid: OWNER2,
      }),
    );
  });
});

describe("migration: thing_shares is now the live ACL (Phase G3)", () => {
  // The inverse of what this file asserted before G3. Everything above already proves thing_shares
  // authorizes — every fixture seeds it. What is left to prove is that the RETIRED tree does not,
  // because a rules file that still honoured aircraft_shares would leave the #204 namespacing hole
  // open on a tree nobody maintains any more.
  //
  // A distinct aircraft id, rather than clearing and re-seeding the shared fixture: this aircraft
  // exists ONLY in the legacy tree, so the assertion cannot accidentally be satisfied (or defeated)
  // by the membership the outer beforeEach grants on AC.
  const LEGACY_ONLY = "ac-legacy-only";
  const legacyOnlyThing = `users/${HOST}/${"aircraft"}/${LEGACY_ONLY}`;
  const legacyOnlyShare = `aircraft_shares/${HOST}/aircraft/${LEGACY_ONLY}`;

  beforeEach(async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, legacyOnlyThing), { registration: "N999", writerUid: HOST });
      // A full, valid-looking ACL — at the old address only.
      await setDoc(doc(db, legacyOnlyShare), {
        hostUid: HOST,
        thingId: LEGACY_ONLY,
        memberRoles: { [HOST]: "owner", [TECH]: "technician" },
      });
    });
  });

  it("grants no entity access from a doc that exists only in the retired tree", async () => {
    await assertFails(getDoc(doc(as(TECH), legacyOnlyThing)));
    await assertFails(getDoc(doc(as(TECH), `users/${HOST}/thing/${LEGACY_ONLY}`)));
  });

  it("grants no ACL access from it either", async () => {
    await assertFails(getDoc(doc(as(TECH), legacyOnlyShare)));
  });

  it("still matches the retired tree for someone thing_shares does vouch for", async () => {
    // The aircraft_shares match block stays in place through the grace window and now resolves
    // membership through thing_shares like everything else — so a CURRENT member can still read
    // what is left there. That is what makes G6 a cleanup rather than a second cutover.
    await assertSucceeds(getDoc(doc(as(TECH), legacyShareDoc)));
  });
});
