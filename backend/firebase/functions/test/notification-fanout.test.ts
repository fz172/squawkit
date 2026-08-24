import { beforeEach, describe, expect, it, vi } from "vitest";

import { adminDb, fft } from "./helpers.js";

import { Aircraft } from "../src/generated/proto/aircraft/aircraft.js";
import { NotificationSettings } from "../src/generated/proto/settings/notification_settings.js";
import { Squawk, SquawkPriority } from "../src/generated/proto/aircraft/squawk.js";
import { bumpActivity } from "../src/notifications/activityCounter.js";
import {
  AIRCRAFT_HOURLY_CEILING,
  activityDocPath,
  rateDocPath,
  type NotificationActivityDoc,
} from "../src/notifications/notificationModels.js";
import {
  onNotifiableAircraftWritten,
  onNotifiableRecordWritten,
} from "../src/notifications/onRecordWritten.js";
import { aircraftShareDocPath, shareMemberDocPath } from "../src/sharing/sharingModels.js";

/**
 * N1 fan-out, end to end against the emulator (design §7, issue P4.11).
 *
 * FCM itself has no emulator, so `firebase-admin/messaging` is mocked and every message the trigger
 * would have sent is captured. That is also the only interesting assertion surface: the whole of
 * §7.3 is a claim about **which notification ids** come out of a burst, not about how many writes
 * happened.
 */
const { sentMessages } = vi.hoisted(() => ({
  sentMessages: [] as { tokens: string[]; data: Record<string, string>; android?: { collapseKey?: string }; apns?: { headers?: Record<string, string> } }[],
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

const wrappedRecord = fft.wrap(onNotifiableRecordWritten);
const wrappedAircraft = fft.wrap(onNotifiableAircraftWritten);

const HOST = "n1-host";
const MEMBER = "n1-member";
const LURKER = "n1-lurker";
const MALLORY = "n1-mallory";
const AC_A = "n1-ac-a";
const AC_B = "n1-ac-b";

const recordPath = (acId: string, kind: string, docId: string) =>
  `users/${HOST}/aircraft/${acId}/${kind}/${docId}`;

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
    Aircraft.encode(Aircraft.fromPartial({ id: acId, tailNumber: tail })).finish(),
    "aircraft.Aircraft",
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
    appVersion: "1.0.0",
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
  const path = `users/${HOST}/aircraft/${acId}`;
  return {
    data: fft.makeChange(
      fft.firestore.makeDocumentSnapshot(before ?? {}, path),
      fft.firestore.makeDocumentSnapshot(after, path),
    ),
    params: { uid: HOST, acId },
  } as never;
}

/** One task edit by the host, which the member should hear about. */
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

async function activityDoc(acId: string, recordType = "task", actor = HOST) {
  const snap = await adminDb.doc(activityDocPath(HOST, acId, recordType as never, actor)).get();
  return snap.exists ? (snap.data() as Partial<NotificationActivityDoc>) : null;
}

function sessionCount(doc: Partial<NotificationActivityDoc> | null | undefined): number {
  return (doc?.writeCount ?? 0) - (doc?.sessionBaseCount ?? 0);
}

/**
 * Clears the per-key send throttle so the next write posts.
 *
 * §7.4's `MIN_REPOST_INTERVAL` would otherwise collapse a fast burst into one send, which is
 * correct behaviour and is asserted on its own below — but it hides the property most of these
 * tests are about, which is what the *ids* do when a burst really does post repeatedly.
 */
async function clearThrottle(acId: string, recordType = "task", actor = HOST) {
  await adminDb
    .doc(activityDocPath(HOST, acId, recordType as never, actor))
    .set({ lastSentAt: null }, { merge: true });
}

/** Ages the session so the next write lands past `ACTIVITY_WINDOW`. */
async function ageSession(acId: string, minutes: number, recordType = "task", actor = HOST) {
  const ref = adminDb.doc(activityDocPath(HOST, acId, recordType as never, actor));
  const doc = (await ref.get()).data() as NotificationActivityDoc;
  const shift = (ts: FirebaseFirestore.Timestamp | null | undefined) =>
    ts == null ? null : new Date(ts.toMillis() - minutes * 60_000);
  await ref.set(
    {
      firstWriteAt: shift(doc.firstWriteAt),
      lastWriteAt: shift(doc.lastWriteAt),
      lastSentAt: shift(doc.lastSentAt),
    },
    { merge: true },
  );
}

const idsOf = () => sentMessages.map((m) => m.data.notificationId);

beforeEach(async () => {
  sentMessages.length = 0;
  await Promise.all([
    adminDb.recursiveDelete(adminDb.doc(`users/${HOST}`)),
    adminDb.recursiveDelete(adminDb.doc(`users/${MEMBER}`)),
    adminDb.recursiveDelete(adminDb.doc(`users/${LURKER}`)),
    adminDb.recursiveDelete(adminDb.collection("aircraft_shares").doc(HOST)),
    adminDb.recursiveDelete(adminDb.collection("aircraft_shares").doc(MALLORY)),
    adminDb.recursiveDelete(adminDb.doc(`users/${MALLORY}`)),
    adminDb.recursiveDelete(adminDb.collection("notification_activity")),
    adminDb.recursiveDelete(adminDb.collection("notification_rate")),
  ]);
  await adminDb.doc(`users/${HOST}/aircraft/${AC_A}`).set(aircraftEnvelope(AC_A, "N4589T"));
  await adminDb.doc(`users/${HOST}/aircraft/${AC_B}`).set(aircraftEnvelope(AC_B, "N771TS"));
  await registerDevice(MEMBER, "install-1", "tok-member");
});

// -------------------------------------------------------------------------------------------------

describe("§7.3 coalescing by replacement", () => {
  it("turns five edits on A interleaved with three on B into exactly two notification ids", async () => {
    // The whole §7.3 argument in one test. A buffered design would have shown Sarah nothing for
    // nine minutes; a leading-edge one would have sent twice and silently lost six edits. Here every
    // write posts, and the tray does the coalescing — so the count is accurate at every intermediate
    // moment, and a finished aircraft's entry is never touched by the other one.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await shareAircraft(AC_B, { [HOST]: "owner", [MEMBER]: "technician" });

    const plan = [AC_A, AC_A, AC_B, AC_A, AC_B, AC_A, AC_B, AC_A];
    const revisions: Record<string, number> = { [AC_A]: 0, [AC_B]: 0 };
    for (const acId of plan) {
      revisions[acId] += 1;
      await taskEdit(acId, revisions[acId]);
      await clearThrottle(acId);
    }

    expect(sentMessages).toHaveLength(8);
    expect(new Set(idsOf()).size).toBe(2);
    expect(sessionCount(await activityDoc(AC_A))).toBe(5);
    expect(sessionCount(await activityDoc(AC_B))).toBe(3);
  });

  it("re-sends the second write under the SAME id and collapse_key, with the count bumped to 2", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    await clearThrottle(AC_A);
    await taskEdit(AC_A, 2);

    expect(sentMessages).toHaveLength(2);
    const [first, second] = sentMessages;
    expect(second.data.notificationId).toBe(first.data.notificationId);
    // All three carry the same value: the id replaces in the tray, the collapse headers make a
    // device that was offline for the whole burst receive only the last message.
    expect(second.android?.collapseKey).toBe(second.data.notificationId);
    expect(second.apns?.headers?.["apns-collapse-id"]).toBe(second.data.notificationId);
    expect(first.data.changeCount).toBe("1");
    expect(second.data.changeCount).toBe("2");
    expect(second.data.bodyKey).toBe("notification_n1_body_plural");
  });

  it("rolls the id — not just the count — once ACTIVITY_WINDOW has elapsed", async () => {
    // Asserting only on the count passes for the broken version. The id is the part that matters:
    // without a rolled `sessionStart`, an edit an hour later OVERWRITES "made 5 changes" with
    // "made a change", destroying news the recipient may never have read.
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    await clearThrottle(AC_A);
    await taskEdit(AC_A, 2);
    const morningId = sentMessages.at(-1)!.data.notificationId;

    await ageSession(AC_A, 31);
    await taskEdit(AC_A, 3);

    const afternoon = sentMessages.at(-1)!;
    expect(afternoon.data.notificationId).not.toBe(morningId);
    expect(afternoon.data.changeCount).toBe("1");
    expect(sessionCount(await activityDoc(AC_A))).toBe(1);
  });
});

describe("§7.4 the counter and its guards", () => {
  it("sends nothing and writes no counter for an unshared aircraft", async () => {
    // The early exit that keeps the whole feature cheap: most writes look exactly like this and
    // cost one document read.
    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(0);
    expect(await activityDoc(AC_A)).toBeNull();
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

  it("collapses two writes inside MIN_REPOST_INTERVAL into one send, still counting both", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);
    await taskEdit(AC_A, 2);

    expect(sentMessages).toHaveLength(1);
    // The cost is a count that lags; the next write corrects it, and the final write of a burst is
    // the one that matters.
    expect(sessionCount(await activityDoc(AC_A))).toBe(2);
  });

  it("gives two concurrent session starts ONE notification id", async () => {
    // Why the id is keyed on a sequence and not on `firstWriteAt`. Both writers read the same
    // previous value and compute the same next one, so they converge. Two clock reads milliseconds
    // apart would not, and the recipient would get two tray entries for one session — one of which
    // nothing ever updates again.
    const input = {
      hostUid: HOST,
      aircraftId: AC_A,
      recordType: "task" as const,
      actorUid: HOST,
      nowMs: Date.now(),
    };
    const [a, b] = await Promise.all([bumpActivity(input), bumpActivity(input)]);

    expect(a.sessionSeq).toBe(b.sessionSeq);
    expect(a.sessionSeq).toBe(1);
  });

  it("leaves changeCount at 2 for two concurrent writes, not 1", async () => {
    // The lock-free path a transaction-shaped test would silently pass. §7.4's pseudocode assigns
    // the literal 1 on a new session, so two writers racing on the FIRST write of a session both
    // write 1 and one edit vanishes. The stored total is only ever incremented, so it cannot.
    const input = {
      hostUid: HOST,
      aircraftId: AC_A,
      recordType: "task" as const,
      actorUid: HOST,
      nowMs: Date.now(),
    };
    await Promise.all([bumpActivity(input), bumpActivity(input)]);

    expect(sessionCount(await activityDoc(AC_A))).toBe(2);
  });

  it("stops sending once the hourly ceiling trips, after one 'a lot of activity' notice", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await adminDb
      .doc(rateDocPath(HOST, AC_A, Date.now()))
      .set({ sendCount: AIRCRAFT_HOURLY_CEILING, expireAt: new Date() });

    await taskEdit(AC_A, 1);
    await taskEdit(AC_A, 2);
    await taskEdit(AC_A, 3);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].data.notificationId).toMatch(/^n1max:/);
    expect(sentMessages[0].data.bodyKey).toBe("notification_n1_body_high_volume");
    // Past the cap a write costs one read and nothing else — no counter document is created.
    expect(await activityDoc(AC_A)).toBeNull();
  });
});

describe("doc ids are namespaced under the host (#204)", () => {
  /**
   * The aircraft id is a 20-character client-generated string that is unique only WITHIN a tree,
   * and the own-tree rule lets anyone create `users/{self}/aircraft/{anyId}`. Keyed on the aircraft
   * id alone, `notification_rate` would be one global namespace that any account could reach into
   * by choosing an id it had seen — and every current and former member of a share knows its
   * aircraft id.
   */
  it("does not let a stranger's identically-named aircraft burn this one's hourly budget", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    // A DIFFERENT host, with an aircraft that has the SAME id. Nothing forbids this.
    await adminDb.doc(aircraftShareDocPath(MALLORY, AC_A)).set({
      hostUid: MALLORY,
      aircraftId: AC_A,
      memberRoles: { [MALLORY]: "owner", [LURKER]: "technician" },
      createdAt: new Date(),
    });
    await registerDevice(LURKER, "lurker-install", "tok-lurker");

    // Mallory exhausts the hourly ceiling in HER tree.
    await adminDb
      .doc(rateDocPath(MALLORY, AC_A, Date.now()))
      .set({ sendCount: AIRCRAFT_HOURLY_CEILING, expireAt: new Date() });
    await wrappedRecord({
      data: fft.makeChange(
        fft.firestore.makeDocumentSnapshot({}, `users/${MALLORY}/aircraft/${AC_A}/maintenance_task/t`),
        fft.firestore.makeDocumentSnapshot(
          taskEnvelope(1, MALLORY),
          `users/${MALLORY}/aircraft/${AC_A}/maintenance_task/t`,
        ),
      ),
      params: { uid: MALLORY, acId: AC_A, kind: "maintenance_task", docId: "t" },
    } as never);
    sentMessages.length = 0;

    // The victim's aircraft must be entirely unaffected: a normal activity notification, not the
    // "a lot of activity" notice a tripped ceiling produces.
    await taskEdit(AC_A, 1);

    expect(sentMessages).toHaveLength(1);
    expect(sentMessages[0].tokens).toEqual(["tok-member"]);
    expect(sentMessages[0].data.notificationId).toMatch(/^n1:/);
  });

  /**
   * The counter key also carries `actorUid`, which rules pin to the writer's own uid — so colliding
   * it needs the SAME person writing in both trees, not merely the same aircraft id. A mechanic who
   * works for two owners is the ordinary way that happens.
   */
  it("keeps two hosts' counters apart when the same actor writes in both", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await adminDb.doc(aircraftShareDocPath(MALLORY, AC_A)).set({
      hostUid: MALLORY,
      aircraftId: AC_A,
      memberRoles: { [MALLORY]: "owner", [MEMBER]: "technician" },
      createdAt: new Date(),
    });

    await taskEdit(AC_A, 1, MEMBER);
    await taskEdit(AC_A, 2, MEMBER);
    await wrappedRecord({
      data: fft.makeChange(
        fft.firestore.makeDocumentSnapshot({}, `users/${MALLORY}/aircraft/${AC_A}/maintenance_task/t`),
        fft.firestore.makeDocumentSnapshot(
          taskEnvelope(1, MEMBER),
          `users/${MALLORY}/aircraft/${AC_A}/maintenance_task/t`,
        ),
      ),
      params: { uid: MALLORY, acId: AC_A, kind: "maintenance_task", docId: "t" },
    } as never);

    // Two separate working sessions, not one run-on count of 3 — and since `firstWriteAt` IS the
    // notification id, a merged document would also let one tree roll the other's tray entry.
    expect(sessionCount(await activityDoc(AC_A, "task", MEMBER))).toBe(2);
    expect(
      sessionCount(
        (await adminDb.doc(activityDocPath(MALLORY, AC_A, "task", MEMBER)).get()).data(),
      ),
    ).toBe(1);
  });
});

describe("§7.5 the escalation bypass", () => {
  it("posts under n1esc:, exempt from the throttle and from the ceiling", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await adminDb
      .doc(rateDocPath(HOST, AC_A, Date.now()))
      .set({ sendCount: AIRCRAFT_HOURLY_CEILING, ceilingNotified: true, expireAt: new Date() });

    await wrappedRecord(
      recordWrite(
        AC_A,
        "squawk",
        "sq-1",
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_LOW),
        squawkEnvelope("sq-1", SquawkPriority.SQUAWK_PRIORITY_AOG),
      ),
    );
    // A second escalation immediately after: no MIN_REPOST_INTERVAL applies to this path.
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
    expect(sentMessages[0].data.channel).toBe("GROUNDED");
    expect(sentMessages[0].data.highPriority).toBe("true");
    expect(sentMessages[0].data.bodyKey).toBe("notification_body_grounded_single");
    expect(sentMessages[0].data.recordTitle).toBe("Left brake dragging");
    expect(sentMessages[1].data.channel).toBe("URGENCY");
    expect(sentMessages[1].data.bodyKey).toBe("notification_body_priority_raised_single");
    // AOG has its own GROUNDED tier, so "priority raised" always reads "to High" — rank 3.
    expect(sentMessages[1].data.toRank).toBe("3");
  });

  it("does not fold an escalation into the activity id or the activity count", async () => {
    // Folding it in would let the next routine edit overwrite "raised to AOG" with "made 4 changes
    // to squawks" — silently replacing a grounding alert with a shrug.
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

    expect(await activityDoc(AC_A, "squawk")).toBeNull();
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
    await clearThrottle(AC_A, "squawk");
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
    await clearThrottle(AC_A);
    await taskEdit(AC_A, 2);

    expect(sentMessages).toHaveLength(1);
  });

  it("honors the recipient's per-class toggle", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await setPreferences(MEMBER, { taskActivityDisabled: true });

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

  it("names the actor from the share roster, and the aircraft by tail number", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });

    await taskEdit(AC_A, 1);

    expect(sentMessages[0].data.actorName).toBe("Dave Chen");
    expect(sentMessages[0].data.tailNumber).toBe("N4589T");
    expect(sentMessages[0].data.recordType).toBe("task");
    expect(sentMessages[0].data.tapTarget).toBe(`aircraft:${AC_A}:tasks`);
  });
});

describe("what is not collaboration activity", () => {
  it("ignores a write with no writerUid — including a function's own tombstone cascade", async () => {
    // `onAircraftDeleted` tombstones every child record of a deleted aircraft. Without this guard
    // that single act would fan out one notification per record.
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

  it("honors the aircraft-activity toggle independently of the others", async () => {
    await shareAircraft(AC_A, { [HOST]: "owner", [MEMBER]: "technician" });
    await setPreferences(MEMBER, { aircraftActivityDisabled: true });

    await wrappedAircraft(
      aircraftWrite(AC_A, aircraftEnvelope(AC_A, "N4589T"), aircraftEnvelope(AC_A, "N123AB")),
    );

    expect(sentMessages).toHaveLength(0);
  });
});
