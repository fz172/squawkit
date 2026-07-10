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

const aircraftDoc = `users/${HOST}/aircraft/${AC}`;
const logDoc = `${aircraftDoc}/maintenance_log/${LOG}`;
const shareDoc = `aircraft_shares/${AC}`;

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
    await setDoc(doc(db, aircraftDoc), { registration: "N123", writerUid: HOST });
    await setDoc(doc(db, logDoc), { note: "oil change", writerUid: HOST });
    await setDoc(doc(db, shareDoc), {
      hostUid: HOST,
      aircraftId: AC,
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
    await assertSucceeds(getDoc(doc(as(TECH), aircraftDoc)));
  });

  it("non-member may NOT get the shared aircraft doc", async () => {
    await assertFails(getDoc(doc(as(STRANGER), aircraftDoc)));
  });

  it("member may NOT LIST the host's aircraft collection (keeps other aircraft private)", async () => {
    await assertFails(getDocs(collection(as(TECH), `users/${HOST}/aircraft`)));
  });

  it("co-owner may edit the aircraft doc (attested, non-delete)", async () => {
    await assertSucceeds(
      setDoc(doc(as(OWNER2), aircraftDoc), { registration: "N999", deleted: false, writerUid: OWNER2 }),
    );
  });

  it("technician may NOT write the aircraft doc", async () => {
    await assertFails(
      setDoc(doc(as(TECH), aircraftDoc), { registration: "N999", writerUid: TECH }),
    );
  });

  it("co-owner may NOT forge writerUid on the aircraft doc", async () => {
    await assertFails(
      setDoc(doc(as(OWNER2), aircraftDoc), { registration: "N999", writerUid: HOST }),
    );
  });

  it("hosting owner may delete (tombstone) the aircraft", async () => {
    await assertSucceeds(
      setDoc(doc(as(HOST), aircraftDoc), { registration: "N123", deleted: true, writerUid: HOST }),
    );
  });

  it("co-owner may NOT delete (tombstone) the aircraft — hosting owner only", async () => {
    await assertFails(
      setDoc(doc(as(OWNER2), aircraftDoc), { registration: "N123", deleted: true, writerUid: OWNER2 }),
    );
  });
});

describe("nested maintenance data", () => {
  it("member may read a log", async () => {
    await assertSucceeds(getDoc(doc(as(TECH), logDoc)));
  });

  it("member may LIST the nested maintenance_log collection", async () => {
    await assertSucceeds(getDocs(collection(as(TECH), `${aircraftDoc}/maintenance_log`)));
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

  it("member may NOT write an unknown kind into the host's subtree", async () => {
    await assertFails(
      setDoc(doc(as(TECH), `${aircraftDoc}/evil/x`), { data: 1, writerUid: TECH }),
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

  it("host may create a share for an aircraft in their own tree", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${HOST}/aircraft/ac-new`), { registration: "N2" });
    });
    await assertSucceeds(
      setDoc(doc(as(HOST), "aircraft_shares/ac-new"), {
        hostUid: HOST,
        aircraftId: "ac-new",
        memberRoles: { [HOST]: "owner" },
      }),
    );
  });

  it("cannot create a share for an aircraft you don't own (spoof)", async () => {
    await assertFails(
      setDoc(doc(as(STRANGER), "aircraft_shares/ac-new"), {
        hostUid: STRANGER,
        aircraftId: "ac-new",
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("cannot create a share claiming a different hostUid (spoof)", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `users/${STRANGER}/aircraft/ac-new`), { registration: "N2" });
    });
    await assertFails(
      setDoc(doc(as(STRANGER), "aircraft_shares/ac-new"), {
        hostUid: HOST,
        aircraftId: "ac-new",
        memberRoles: { [STRANGER]: "owner" },
      }),
    );
  });

  it("no client may update or delete the share root (function-managed)", async () => {
    await assertFails(updateDoc(doc(as(HOST), shareDoc), { memberRoles: { [HOST]: "owner" } }));
    await assertFails(deleteDoc(doc(as(HOST), shareDoc)));
  });
});

describe("member documents", () => {
  it("member may read another member's doc", async () => {
    await assertSucceeds(getDoc(doc(as(OWNER2), `${shareDoc}/members/${TECH}`)));
  });

  it("member may update their own display, keeping role unchanged", async () => {
    await assertSucceeds(
      updateDoc(doc(as(TECH), `${shareDoc}/members/${TECH}`), { displayName: "New Name" }),
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

  it("no client may create or delete member docs (function-managed)", async () => {
    await assertFails(
      setDoc(doc(as(HOST), `${shareDoc}/members/new-uid`), { role: "technician" }),
    );
    await assertFails(deleteDoc(doc(as(HOST), `${shareDoc}/members/${TECH}`)));
  });
});

describe("invites", () => {
  const inviteDoc = `${shareDoc}/invites/token-hash-1`;

  it("owner may create and read invites", async () => {
    await assertSucceeds(
      setDoc(doc(as(OWNER2), inviteDoc), { role: "technician", createdBy: OWNER2 }),
    );
    await assertSucceeds(getDoc(doc(as(OWNER2), inviteDoc)));
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
