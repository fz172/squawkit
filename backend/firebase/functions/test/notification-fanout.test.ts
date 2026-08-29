import { beforeEach, describe, expect, it, vi } from "vitest";

import { adminDb, fft } from "./helpers.js";

import { Thing } from "../src/generated/proto/thing/thing.js";
import { NotificationSettings } from "../src/generated/proto/settings/notification_settings.js";
import {
  Squawk,
  SquawkDismissReason,
  SquawkPriority,
} from "../src/generated/proto/thing/squawk.js";
import { activityNotificationId, RECORD_TYPE } from "../src/notifications/notificationModels.js";
import {
  onNotifiableThingRecordWritten,
  onNotifiableThingWritten,
} from "../src/notifications/onRecordWritten.js";
import { aircraftShareDocPath, shareMemberDocPath } from "../src/sharing/sharingModels.js";

/**
 * N1 fan-out, end to end against the emulator (design §7, issue P4.11).
 *
 * FCM itself has no emulator, so `firebase-admin/messaging` is mocked and every message the trigger
 * would have sent is captured. That is also the only interesting assertion surface: the whole of
 * §7.2 is a claim about **which notification ids** and **which body** come out of a write, not
 * about how many writes happened.
 */
const { sentMessages } = vi.hoisted(() => ({
  sentMessages: [] as {
    tokens: string[];
    data: Record<string, string>;
    android?: { collapseKey?: string };
    apns?: {
      headers?: Record<string, string>;
      payload?: { aps?: { alert?: { title?: string; body?: string }; mutableContent?: boolean } };
    };
  }[],
}));

vi.mock("firebase-admin/messaging", () => ({
  getMessaging: () => ({
    sendEachForMulticast: async (message: (typeof sentMessages)[number]) => {
      sentMessages.push(message);
      return {
        successCount: message.tokens.length,
        failureCount: 0,
        responses: message.tokens.map(() => ({ success: true })),
      };
    },
  }),
}));

const wrappedRecord = fft.wrap(onNotifiableThingRecordWritten);
const wrappedAircraft = fft.wrap(onNotifiableThingWritten);

const HOST = "n1-host";
const MEMBER = "n1-member";
const LURKER = "n1-lurker";
const MALLORY = "n1-mallory";
const AC_A = "n1-ac-a";
const AC_B = "n1-ac-b";

const recordPath = (acId: string, kind: string, docId: string) =>
  `users/${HOST}/thing/${acId}/${kind}/${docId}`;

// --- Seeding -----------------------------------------------------------------------------------

/** The envelope the sync engine writes: a base64 payload string, never raw bytes. */
function envelope(
  payload: Uint8Array,
  schema: string,
  writerUid: string | undefined,
  deleted = false,
) {
  return {
    payload: Buffer.from(payload).toString("base64"),
    schema,
    deleted,
    ...(writerUid == null ? {} : { writerUid }),
  };
}

function aircraftEnvelope(acId: string, tail: string, writerUid = HOST) {
  return envelope(
    Thing.encode(Thing.fromPartial({ id: acId, tailNumber: tail })).finish(),
    "thing.Thing",
    writerUid,
  );
}

function squawkEnvelope(
  id: string,
  priority: SquawkPriority,
  writerUid = HOST,
  title = "Left brake dragging",
) {
  return envelope(
    Squawk.encode(Squawk.fromPartial({ id, title, priority })).finish(),
    "aircraft.Squawk",
    writerUid,
  );
}

/** A squawk that has been dismissed — rank 0, so reopening it is a raise rather than a creation. */
function dismissedEnvelope(id: string, writerUid = HOST) {
  return envelope(
    Squawk.encode(
      Squawk.fromPartial({
        id,
        title: "Left brake dragging",
        priority: SquawkPriority.SQUAWK_PRIORITY_AOG,
        dismissReason: SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
      }),
    ).finish(),
    "aircraft.Squawk",
    writerUid,
  );
}

/** A task envelope whose bytes differ per revision, so `isMeaningfulChange` sees a real edit. */
function taskEnvelope(revision: number, writerUid = HOST) {
  return envelope(new Uint8Array([revision & 0xff]), "aircraft.MaintenanceTask", writerUid);
}

async function shareAircraft(acId: string, memberRoles: Record<string, string>) {
  await adminDb.doc(aircraftShareDocPath(HOST, acId)).set({
    hostUid: HOST,
    aircraftId: acId,
    memberRoles,
    createdAt: new Date(),
  });
  await adminDb.doc(shareMemberDocPath(HOST, acId, HOST)).set({
    role: "owner",
    displayName: "Dave Chen",
    addedAt: new Date(),
    invitedBy: HOST,
  });
}

async function registerDevice(uid: string, installationId: string, token: string, enabled = true) {
  await adminDb.doc(`users/${uid}/${"push_devices"}/${installationId}`).set({
    token,
    platform: "android",
    enabled,
    updatedAt: new Date(),
  });
}

async function setPreferences(uid: string, settings: Partial<NotificationSettings>) {
  const payload = NotificationSettings.encode(NotificationSettings.fromPartial(settings)).finish();
  await adminDb.doc(`users/${uid}/notification_settings/main`).set({
    payload: Buffer.from(payload).toString("base64"),
    schema: "settings.NotificationSettings",
    deleted: false,
  });
}

// --- Driving the trigger -------------------------------------------------------------------------

function recordWrite(
  acId: string,
  kind: string,
  docId: string,
  before: object | null,
  after: object,
) {
  const path = recordPath(acId, kind, docId);
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot(before ?? {}, path),
      fft.firestore.makeDocumentSnapshot(after, path),
    ),
    params: { uid: HOST, acId, kind, docId },
  } as never;
}

function aircraftWrite(acId: string, before: object | null, after: object) {
  const path = `users/${HOST}/thing/${acId}`;
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot(before ?? {}, path),
      fft.firestore.makeDocumentSnapshot(after, path),
    ),
    params: { uid: HOST, acId },
  } as never;
}

/** One task edit by the host, which the member should hear about. Revision 1 is a creation. */
async function taskEdit(acId: string, revision: number, actor = HOST) {
  await wrappedRecord(
    recordWrite(
      acId,
      "maintenance_task",
      "task-1",
      revision === 1 ? null : taskEnvelope(revision - 1, actor),
      taskEnvelope(revision, actor),
    ),
  );
}

const idsOf = () => sentMessages.map((m) => m.data.notificationId);

beforeEach(async () => {
  sentMessages.length = 0;
  await Promise.all([
    adminDb.recursiveDelete(adminDb.doc(`users/${HOST}`)),
    adminDb.recursiveDelete(adminDb.doc(`users/${MEMBER}`)),
    adminDb.recursiveDelete(adminDb.doc(`users/${LURKER}`)),
    adminDb.recursiveDelete(adminDb.collection("thing_shares").doc(HOST)),
    adminDb.recursiveDelete(adminDb.collection("thing_shares").doc(MALLORY)),
    adminDb.recursiveDelete(adminDb.doc(`users/${MALLORY}`)),
  ]);
  await adminDb.doc(`users/${HOST}/thing/${AC_A}`).set(aircraftEnvelope(AC_A, "N4589T"));
  await adminDb.doc(`users/${HOST}/thing/${AC_B}`).set(aircraftEnvelope(AC_B, "N771TS"));
  await registerDevice(MEMBER, "install-1", "tok-member");
});

// -------------------------------------------------------------------------------------------------

describe("§7.2 one concrete notification per write (coalescing removed, 2026-08-27)", () => {
  // The earlier design (§7.3, now historical) shared one counter per (aircraft, recordType, actor)
  // across every recipient and replaced the tray entry in place, summarizing as "made N changes."
  // That lost the specific record a pilot had already looked at the moment a second, unrelated
  // write replaced it with a bigger, vaguer number. Every write now sends its own notification,
  // naming the record and what happened to it, and nothing here ever collapses one onto another.

  it("names the record it creates", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_record_created");
    expect(sentMessages[0].data.recordId).toBe("task-1");
  });

  it("names the record it updates, not created", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    await taskEdit(AC_A, 2);

    expect(sentMessages[1].data.bodyKey).toBe("notification_n1_body_record_updated");
  });

  it("names the record it deletes, and taps to the aircraft/tab instead of the gone record", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-1", taskEnvelope(1), {
        ...taskEnvelope(1),
        deleted: true,
      }),
    );

    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_record_deleted");
    expect(sentMessages[0].data.tapTarget).toBe(`aircraft:${AC_A}:tasks`);
  });

  it("taps a created or updated record's notification straight to that record", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);

    expect(sentMessages[0].data.tapTarget).toBe(`task:${AC_A}:task-1`);
  });

  it("keeps the notification id under FCM's 64-byte apns-collapse-id limit, worst case", () => {
    // Regression for the 2026-08-27 outage: `sendPush` sets `apns-collapse-id` to this id, and FCM
    // hard-rejects the WHOLE multicast — every platform, not just iOS — when it exceeds 64 bytes.
    // That shipped once already: an id that embedded `aircraftId` ran 65 bytes for `recordType:
    // "squawk"` and 67 for `"aircraft"` (whose recordId duplicates a 20-char aircraftId), both over
    // the limit, and the failure is silent — `sendToRecipient` catches and logs it, the record write
    // itself still succeeds, and nothing points at the push. Real ids are 20-char client-generated
    // strings (`IdGenerator.kt`); "aircraft" is the longest `recordType` and the worst case, since its
    // `recordId` IS the aircraftId. `atMs` at 13 digits (current epoch millis) is the longest it gets
    // for centuries.
    const worstCase = activityNotificationId(
      RECORD_TYPE.AIRCRAFT,
      "a".repeat(20),
      Date.now(),
    );

    expect(Buffer.byteLength(worstCase, "utf8")).toBeLessThanOrEqual(64);
  });

  it("gives every write its own notification id, even two writes to the same record", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    await taskEdit(AC_A, 2);
    await taskEdit(AC_A, 3);

    expect(sentMessages).toHaveLength(3);
    // Nothing collapses in the tray: three distinct ids, not one replaced twice.
    expect(new Set(idsOf()).size).toBe(3);
  });

  it("sends one push per write, with no throttle and no ceiling — a rapid burst sends every one", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    for (let revision = 1; revision <= 6; revision += 1) {
      await taskEdit(AC_A, revision);
    }

    expect(sentMessages).toHaveLength(6);
  });

  it("sends nothing and touches nothing for an unshared aircraft", async () => {
    // The early exit that keeps the whole feature cheap: most writes look exactly like this and
    // cost one document read.
    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(0);
  });

  it("sends nothing when the last member left but the ACL document survives", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner" });

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(0);
  });

  it("excludes the actor from the audience", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await registerDevice(HOST, "host-install", "tok-host");

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].tokens).toEqual(["tok-member"]);
  });

  it("notifies the HOST when a member is the actor", async () => {
    // The owner hearing about their mechanic's edits is the case the feature exists for.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await registerDevice(HOST, "host-install", "tok-host");

    await taskEdit(AC_A, 1, MEMBER);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].tokens).toEqual(["tok-host"]);
  });

  it("keeps two aircraft's notifications entirely separate", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await shareAircraft(AC_B, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-a", null, taskEnvelope(1)),
    );
    await wrappedRecord(
      recordWrite(AC_B, "maintenance_task", "task-b", null, taskEnvelope(1)),
    );

    expect(sentMessages).toHaveLength(2);
    expect(new Set(sentMessages.map((m) => m.data.aircraftId))).toEqual(new Set([AC_A, AC_B]));
  });
});

describe("§7.5 the escalation bypass", () => {
  it("posts under n1esc:, its own id never touched by the activity path", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-1",
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_LOW),
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_AOG),
      ),
    );
    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-2",
        squawkEnvelope("sq-2", SquawkPriority.SQUAWK_PRIORITY_LOW),
        squawkEnvelope("sq-2", SquawkPriority.SQUAWK_PRIORITY_HIGH),
      ),
    );

    expect(idsOf()).toEqual([`n1esc:${AC_A}:sq-1`, `n1esc:${AC_A}:sq-2`]);
    // AOG is not its own tier — it reports exactly like any other priority raise (design decision,
    // 2026-08-26): same channel, same (non-high) priority as the HIGH escalation right after it.
    expect(sentMessages[0].data.channel).toBe("URGENCY");
    expect(sentMessages[0].data.highPriority).toBe("false");
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_squawk_raised");
    expect(sentMessages[0].data.recordTitle).toBe("Left brake dragging");
    expect(sentMessages[1].data.channel).toBe("URGENCY");
    expect(sentMessages[1].data.highPriority).toBe("false");
    expect(sentMessages[1].data.bodyKey).toBe("notification_n1_body_squawk_raised");
  });

  it("keys the id on the document id, not the payload's own id field", async () => {
    // Nothing enforces that the two agree — rules cannot read a payload. proto3 defaults an unset
    // `id` to "", which would put every grounding alert on this aircraft under `n1esc:{ac}:`  and
    // let each one replace the last. And a member could carry another squawk's id to overwrite
    // that alert deliberately. The path is the record's identity; the payload copy is a claim.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    const unset = (priority: SquawkPriority) =>
      envelope(
        Squawk.encode(Squawk.fromPartial({ title: "Left brake dragging", priority })).finish(),
        "aircraft.Squawk",
        HOST,
      );

    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-real-id",
        unset(SquawkPriority.SQUAWK_PRIORITY_LOW),
        unset(SquawkPriority.SQUAWK_PRIORITY_AOG),
      ),
    );

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].data.notificationId).toBe(`n1esc:${AC_A}:sq-real-id`);
    expect(sentMessages[0].data.recordId).toBe("sq-real-id");
    expect(sentMessages[0].data.tapTarget).toBe(`squawk:${AC_A}:sq-real-id`);
  });

  it("names the actor, and never reuses an N2 body", async () => {
    // Both fire for one squawk — this within seconds, N2 at the recipient's next scan — and they are
    // deliberately not deduplicated. What keeps the second from reading as a duplicate is that only
    // the server knows who did it, so only this body can say so. Sharing N2's wording would throw
    // that away.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-1",
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_LOW),
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_AOG),
      ),
    );

    expect(sentMessages[0].data.actorName).toBe("Dave Chen");
    expect(sentMessages[0].data.bodyKey).toMatch(/^notification_n1_/);
    // The N2 bodies carry no actor, so nothing may fall back to them.
    expect(sentMessages[0].data.bodyKey).not.toBe("notification_body_priority_raised_single");
  });

  it("tells a squawk created at AOG apart from one raised to it — same as any other priority", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    // No prior document at all: nobody raised anything. AOG gets the same "created" title HIGH
    // does — it is not its own headline (design decision, 2026-08-26).
    await wrappedRecord(
      recordWrite(AC_A, "squawk", "sq-new", null, squawkEnvelope("sq-new", SquawkPriority.SQUAWK_PRIORITY_AOG)),
    );
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_squawk_created");
    expect(sentMessages[0].data.titleKey).toBe("notification_n1_title_squawk_created");

    sentMessages.length = 0;
    // Created straight at HIGH takes its own title — "Priority raised" would contradict the body.
    await wrappedRecord(
      recordWrite(AC_A, "squawk", "sq-high", null, squawkEnvelope("sq-high", SquawkPriority.SQUAWK_PRIORITY_HIGH)),
    );
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_squawk_created");
    expect(sentMessages[0].data.titleKey).toBe("notification_n1_title_squawk_created");

    sentMessages.length = 0;
    // A reopen is a RAISE: the record was already there, only its status changed.
    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-reopened",
        dismissedEnvelope("sq-reopened"),
        squawkEnvelope("sq-reopened", SquawkPriority.SQUAWK_PRIORITY_AOG),
      ),
    );
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_squawk_raised");
  });

  it("stays silent for a bump that lands below HIGH, and for a de-escalation", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-1",
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_LOW),
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_MEDIUM),
      ),
    );
    expect(idsOf().every((id) => id.startsWith("n1:"))).toBe(true);

    sentMessages.length = 0;
    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-1",
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_AOG),
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_LOW),
      ),
    );
    // A de-escalation is routine activity, never an urgency notification.
    expect(idsOf().every((id) => id.startsWith("n1:"))).toBe(true);
  });
});

describe("§7.4 audience and preferences, re-derived on every send", () => {
  it("drops a member revoked between two writes", async () => {
    // With nothing buffered there is no cached audience that could outlive the revocation, so
    // PRD §9.5 is a property of the shape rather than a rule to enforce.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    expect(sentMessages).toHaveLength(1);

    await adminDb.doc(aircraftShareDocPath(HOST, AC_A)).set(
      { memberRoles: { [HOST]: "owner" } },
      { merge: false },
    );
    await taskEdit(AC_A, 2);

    expect(sentMessages).toHaveLength(1);
  });

  it("honors the recipient's collaboration toggle", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await setPreferences(MEMBER, { collaborationDisabled: true });

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(0);
  });

  it("honors the master switch", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await setPreferences(MEMBER, { allDisabled: true });

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(0);
  });

  it("treats an absent preferences document as all-on", async () => {
    // Every field in the proto is inverted precisely so this is the answer.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(1);
  });

  it("skips a device the user silenced, and still reaches their other one", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await registerDevice(MEMBER, "install-2", "tok-ipad", false);
    await registerDevice(MEMBER, "install-3", "tok-phone");

    await taskEdit(AC_A, 1);

    expect(sentMessages[0].tokens.sort()).toEqual(["tok-member", "tok-phone"]);
  });

  it("addresses each copy to the recipient it is for", async () => {
    // P4.13. An FCM token belongs to the app install, not the account, so a device can hold a live
    // token registered under an account that signed out here. Naming the recipient is what lets the
    // client drop a message meant for somebody else.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician", [LURKER]: "technician" });
    await registerDevice(LURKER, "lurker-install", "tok-lurker");

    await taskEdit(AC_A, 1);

    // One multicast per recipient, each carrying its own address and the same notification id.
    expect(sentMessages).toHaveLength(2);
    const byToken = new Map(sentMessages.map((m) => [m.tokens[0], m.data.recipientUid]));
    expect(byToken.get("tok-member")).toBe(MEMBER);
    expect(byToken.get("tok-lurker")).toBe(LURKER);
    expect(new Set(idsOf()).size).toBe(1);
  });

  it("gives one recipient's two devices a single copy, addressed once", async () => {
    // Grouping is per recipient, not per device: two of the same person's phones stay in one
    // multicast, because the address they need is the same.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await registerDevice(MEMBER, "install-3", "tok-phone");

    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].tokens.sort()).toEqual(["tok-member", "tok-phone"]);
    expect(sentMessages[0].data.recipientUid).toBe(MEMBER);
  });

  it("still reaches everyone else when one recipient cannot be resolved", async () => {
    // Without a per-recipient guard, one transient failure rejects the whole Promise.all and NOBODY
    // is notified. Simulated by making one recipient's push_devices read throw.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician", [LURKER]: "technician" });
    await registerDevice(LURKER, "lurker-install", "tok-lurker");

    const realCollection = adminDb.collection.bind(adminDb);
    const spy = vi
      .spyOn(adminDb, "collection")
      .mockImplementation((path: string) =>
        path === `users/${LURKER}/push_devices`
          ? ({ get: () => Promise.reject(new Error("transient")) } as never)
          : realCollection(path),
      );
    try {
      await taskEdit(AC_A, 1);
    } finally {
      spy.mockRestore();
    }

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].tokens).toEqual(["tok-member"]);
  });

  it("names the actor from the share roster, and the aircraft by tail number", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);

    expect(sentMessages[0].data.actorName).toBe("Dave Chen");
    expect(sentMessages[0].data.tailNumber).toBe("N4589T");
    expect(sentMessages[0].data.recordType).toBe("task");
  });
});

describe("what is not collaboration activity", () => {
  it("ignores a write with no writerUid at all", async () => {
    // A pre-attestation document, or a function write that creates a document outright.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    const { writerUid: _stamped, ...unattested } = taskEnvelope(1);
    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-1", taskEnvelope(1), {
        ...unattested,
        deleted: true,
      }),
    );

    expect(sentMessages).toHaveLength(0);
  });

  /**
   * The cascade in its REAL shape, which the `writerUid` guard does not catch.
   *
   * `onAircraftDeleted.tombstoneChildren` uses `batch.update`, so each record keeps its original
   * `writerUid` and the write is indistinguishable from that author deleting it by hand. What keeps
   * it silent is that `tearDownShare` runs FIRST and removes the ACL.
   *
   * **This test does not pin that ordering, and should not be read as doing so.** It pins the shape
   * — a cascade write carries a `writerUid`, so the guard above is not what saves us, and an absent
   * ACL is. The ordering itself is a race between two independent triggers in production, which no
   * unit test at this level can settle; the warning lives in `onAircraftDeleted` next to the two
   * calls whose order decides it.
   */
  it("stays silent for the aircraft-delete cascade, which keeps its writerUid", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await adminDb.doc(`users/${HOST}/thing/${AC_A}/maintenance_task/task-1`).set(taskEnvelope(1));

    // tearDownShare's effect: the ACL is gone before any child is tombstoned.
    await adminDb.recursiveDelete(adminDb.collection("thing_shares").doc(HOST));

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-1", taskEnvelope(1), {
        ...taskEnvelope(1), // writerUid preserved, exactly as batch.update leaves it
        deleted: true,
      }),
    );

    expect(sentMessages).toHaveLength(0);
  });

  it("ignores a hard delete — that is the tombstone GC, not a person", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    const path = recordPath(AC_A, "maintenance_task", "task-1");

    await wrappedRecord({
      data: fft.makeChange(
        fft.firestore.makeDocumentSnapshot({ ...taskEnvelope(1), deleted: true }, path),
        fft.firestore.makeDocumentSnapshot({}, path),
      ),
      params: { uid: HOST, acId: AC_A, kind: "maintenance_task", docId: "task-1" },
    } as never);

    expect(sentMessages).toHaveLength(0);
  });

  it("ignores a re-push that changed nothing", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-1", taskEnvelope(1), taskEnvelope(1)),
    );

    expect(sentMessages).toHaveLength(0);
  });

  it("ignores maintenance_overview, which no settings toggle covers", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_overview", "ov-1", null, taskEnvelope(1)),
    );

    expect(sentMessages).toHaveLength(0);
  });

  it("reports a record deletion, which IS activity", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedRecord(
      recordWrite(AC_A, "maintenance_task", "task-1", taskEnvelope(1), {
        ...taskEnvelope(1),
        deleted: true,
      }),
    );

    expect(sentMessages).toHaveLength(1);
  });
});

describe("the Aircraft record's own trigger", () => {
  it("reports an aircraft edit under the aircraft class, using the written tail number", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedAircraft(
      aircraftWrite(AC_A, aircraftEnvelope(AC_A, "N4589T"), aircraftEnvelope(AC_A, "N123AB")),
    );

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].data.recordType).toBe("aircraft");
    expect(sentMessages[0].data.tailNumber).toBe("N123AB");
    // The aircraft has no per-record title to name, so it gets its own body regardless of kind.
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_aircraft_updated");
    expect(sentMessages[0].data.tapTarget).toBe(`aircraft:${AC_A}:overview`);
  });

  it("stays silent for a tombstoned aircraft — deleting it tears the share down", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await wrappedAircraft(
      aircraftWrite(AC_A, aircraftEnvelope(AC_A, "N4589T"), {
        ...aircraftEnvelope(AC_A, "N4589T"),
        deleted: true,
      }),
    );

    expect(sentMessages).toHaveLength(0);
  });

  it("honors the collaboration toggle on the aircraft document's own trigger too", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await setPreferences(MEMBER, { collaborationDisabled: true });

    await wrappedAircraft(
      aircraftWrite(AC_A, aircraftEnvelope(AC_A, "N4589T"), aircraftEnvelope(AC_A, "N123AB")),
    );

    expect(sentMessages).toHaveLength(0);
  });
});
