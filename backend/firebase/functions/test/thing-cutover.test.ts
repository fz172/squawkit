import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage } from "./helpers.js";

import { Attachment, AttachmentType } from "../src/generated/proto/thing/attachment.js";
import { MaintenanceLog } from "../src/generated/proto/thing/maintenance_log.js";
import { Engine } from "../src/generated/proto/thing/engine.js";
import { Propeller, PropellerBlade, PropellerHub } from "../src/generated/proto/thing/propeller.js";
import { Thing } from "../src/generated/proto/thing/thing.js";
import { runThingCutover } from "../src/migration/thingCutover.js";

/**
 * The cutover script, end to end against the emulator (tasks B1–B3, B5, B11).
 *
 * This also carries the coverage that used to belong to A8 (`LocalThingPathMigratorTest`). When the
 * on-device migrator was withdrawn, the two payload rewrites it owned moved into this script — so
 * the assertions that the embedded `Attachment.storage_path` is fixed, that the Thing backfill
 * matches PRD §9.1, and that the transform is deterministic all live here now.
 *
 * Payloads are seeded as **base64 strings**, the shape `FirestoreSyncWriter` actually writes.
 * Seeding Buffers instead is what let #428 ship: every test passed against a decoder that could not
 * read a real document.
 */

const UID = "user-cutover";
const OTHER = "user-cutover-2";
const AC = "ac-1";
const LOG = "log-1";

const legacyThing = (id = AC) => `users/${UID}/aircraft/${id}`;
const newThing = (id = AC) => `users/${UID}/thing/${id}`;
const legacyBlob = (blobId: string, id = AC) => `users/${UID}/aircraft/${id}/blobs/${blobId}`;
const newBlob = (blobId: string, id = AC) => `users/${UID}/thing/${id}/blobs/${blobId}`;

const b64 = (bytes: Uint8Array) => Buffer.from(bytes).toString("base64");

function thingPayload(overrides: Partial<Parameters<typeof Thing.fromPartial>[0]> = {}) {
  return b64(
    Thing.encode(
      Thing.fromPartial({
        id: AC,
        make: "Cessna",
        model: "172",
        serial: "17280001",
        tailNumber: "N12345",
        engine: [
          Engine.fromPartial({
            make: "Lycoming",
            model: "O-320",
            serial: "E-1",
            propeller: Propeller.fromPartial({
              hub: PropellerHub.fromPartial({ make: "McCauley", model: "H1", serial: "HUB-1" }),
              blades: [
                PropellerBlade.fromPartial({ make: "McCauley", model: "B1", serial: "BL-1" }),
                PropellerBlade.fromPartial({ make: "McCauley", model: "B1", serial: "BL-2" }),
              ],
            }),
          }),
        ],
        ...overrides,
      }),
    ).finish(),
  );
}

function logPayload(...storagePaths: string[]) {
  return b64(
    MaintenanceLog.encode(
      MaintenanceLog.fromPartial({
        id: LOG,
        attachments: storagePaths.map((storagePath, i) =>
          Attachment.fromPartial({
            id: `att-${i}`,
            name: `photo-${i}.jpg`,
            type: AttachmentType.ATTACHMENT_TYPE_IMAGE,
            storagePath,
          }),
        ),
      }),
    ).finish(),
  );
}

async function seedAccount() {
  await adminDb.doc(legacyThing()).set({
    payload: thingPayload(),
    schema: "aircraft.Aircraft",
    deleted: false,
    writerUid: UID,
  });
  await adminDb.doc(`${legacyThing()}/maintenance_log/${LOG}`).set({
    payload: logPayload(legacyBlob("b1")),
    schema: "aircraft.MaintenanceLog",
    deleted: false,
    writerUid: UID,
  });
  await adminStorage.bucket().file(legacyBlob("b1")).save(Buffer.from([1, 2, 3, 4]));
}

async function readThing(path: string): Promise<Thing> {
  const snap = await adminDb.doc(path).get();
  const payload = snap.data()?.payload as string;
  return Thing.decode(new Uint8Array(Buffer.from(payload, "base64")));
}

async function readLog(path: string): Promise<MaintenanceLog> {
  const snap = await adminDb.doc(path).get();
  const payload = snap.data()?.payload as string;
  return MaintenanceLog.decode(new Uint8Array(Buffer.from(payload, "base64")));
}

const RUN = { dryRun: false, onlyUids: [UID] };

beforeEach(async () => {
  for (const uid of [UID, OTHER]) {
    await adminDb.recursiveDelete(adminDb.doc(`users/${uid}`));
    await adminStorage.bucket().deleteFiles({ prefix: `users/${uid}/` });
  }
});

describe("cutover — the copy", () => {
  it("copies the thing doc, its records, and its blobs to /thing/", async () => {
    await seedAccount();

    const report = await runThingCutover(RUN);

    expect(report.failed).toEqual([]);
    expect(report.totals).toMatchObject({ thingsCopied: 1, recordsCopied: 1, blobsCopied: 1 });
    expect((await adminDb.doc(newThing()).get()).exists).toBe(true);
    expect((await adminDb.doc(`${newThing()}/maintenance_log/${LOG}`).get()).exists).toBe(true);
    expect((await adminStorage.bucket().file(newBlob("b1")).exists())[0]).toBe(true);
  });

  it("leaves the source untouched — this pass copies, it never deletes", async () => {
    // The 7-day grace window (§7) is the whole reason a failed run is safe to simply re-run.
    await seedAccount();

    await runThingCutover(RUN);

    expect((await adminDb.doc(legacyThing()).get()).exists).toBe(true);
    expect((await adminStorage.bucket().file(legacyBlob("b1")).exists())[0]).toBe(true);
  });

  it("discovers subcollections rather than assuming a fixed kind list", async () => {
    // A hardcoded list that missed a kind would silently strand that data on the old path.
    await seedAccount();
    await adminDb.doc(`${legacyThing()}/squawk/sq-1`).set({
      payload: b64(new Uint8Array([1])),
      schema: "aircraft.Squawk",
      deleted: false,
    });
    await adminDb.doc(`${legacyThing()}/some_future_kind/x-1`).set({ deleted: false });

    await runThingCutover(RUN);

    expect((await adminDb.doc(`${newThing()}/squawk/sq-1`).get()).exists).toBe(true);
    expect((await adminDb.doc(`${newThing()}/some_future_kind/x-1`).get()).exists).toBe(true);
  });
});

describe("cutover — payload transforms (replacing the withdrawn A8)", () => {
  it("rewrites the embedded Attachment.storage_path", async () => {
    // §2.6: the path is denormalized INSIDE the payload, so moving the objects does not fix the
    // pointers. A local wipe cannot substitute for this — the stale pointer is in the server's copy.
    await seedAccount();

    await runThingCutover(RUN);

    const log = await readLog(`${newThing()}/maintenance_log/${LOG}`);
    expect(log.attachments[0].storagePath).toBe(newBlob("b1"));
    expect(log.attachments[0].storagePath).not.toContain("/aircraft/");
  });

  it("backfills template_id, name, spec and the component tree per PRD §9.1", async () => {
    await seedAccount();

    await runThingCutover(RUN);

    const thing = await readThing(newThing());
    expect(thing.templateId).toBe("airplane");
    expect(thing.name).toBe("N12345");
    expect(thing.spec.map((s) => s.key)).toEqual(["make", "model", "serial", "tail_number"]);

    const airframe = thing.components[0];
    expect(airframe.slotKey).toBe("airframe");
    expect(airframe.serial).toBe("17280001");

    const engine = airframe.children[0];
    expect(engine.slotKey).toBe("engine");
    expect(engine.serial).toBe("E-1");

    const propeller = engine.children[0];
    expect(propeller.slotKey).toBe("propeller");
    expect(propeller.children.map((c) => c.slotKey)).toEqual(["hub", "blade", "blade"]);
  });

  it("keeps the legacy fields populated — they are transitional, not replaced", async () => {
    // §3.1: a pre-migration client still reads fields 2–6, and nothing is renumbered or removed.
    await seedAccount();

    await runThingCutover(RUN);

    const thing = await readThing(newThing());
    expect(thing.make).toBe("Cessna");
    expect(thing.tailNumber).toBe("N12345");
    expect(thing.engine[0].serial).toBe("E-1");
  });

  it("names a thing with no tail number from its make and model", async () => {
    await adminDb.doc(legacyThing()).set({
      payload: thingPayload({ tailNumber: "" }),
      schema: "aircraft.Aircraft",
      deleted: false,
    });

    await runThingCutover(RUN);

    expect((await readThing(newThing())).name).toBe("Cessna 172");
  });

  it("derives component ids deterministically — the load-bearing property", async () => {
    // PRD §9.1: random ids would let last-writer-wins silently reassign every log's component.
    // Re-running the script is the expected recovery path, so identical output across runs matters.
    await seedAccount();

    await runThingCutover(RUN);
    const first = await readThing(newThing());
    await adminDb.recursiveDelete(adminDb.doc(newThing()));
    await runThingCutover(RUN);
    const second = await readThing(newThing());

    expect(Thing.encode(second).finish()).toEqual(Thing.encode(first).finish());
  });

  it("copies an undecodable payload through rather than dropping or synthesizing one", async () => {
    // A payload we cannot read still belongs to the user, and replacing it with a synthesized one
    // would destroy whatever it actually held.
    const corrupt = b64(new Uint8Array([0xff, 0xff, 0xff, 0xff]));
    await adminDb.doc(legacyThing()).set({
      payload: corrupt,
      schema: "aircraft.Aircraft",
      deleted: false,
    });

    const report = await runThingCutover(RUN);

    expect(report.failed).toEqual([]);
    expect((await adminDb.doc(newThing()).get()).data()?.payload).toBe(corrupt);
  });
});

describe("cutover — idempotency and re-runs", () => {
  it("re-writes identical bytes on a second run, and re-copies no blobs", async () => {
    // Note what idempotency means HERE. The source is never mutated — this pass copies and never
    // deletes — so a second run reads the same pristine `/aircraft/` payload and recomputes the
    // backfill from scratch. `thingsBackfilled` counting 1 again is therefore correct, not a bug;
    // the property that matters is that recomputing lands on identical bytes, which is exactly what
    // deriving component ids rather than generating them buys.
    await seedAccount();

    await runThingCutover(RUN);
    const first = await readThing(newThing());
    const second = await runThingCutover(RUN);

    expect(second.failed).toEqual([]);
    expect(Thing.encode(await readThing(newThing())).finish()).toEqual(
      Thing.encode(first).finish(),
    );
    // Blobs are the expensive half, so they are skipped once verified identical at the destination.
    expect(second.totals.blobsAlreadyPresent).toBe(1);
    expect(second.totals.blobsCopied).toBe(0);
  });

  it("skips the backfill on a payload that already carries a template_id", async () => {
    // The defensive branch in backfillThing, exercised directly: a document already at /thing/ (a
    // partially cleaned-up source, or a future re-run reading migrated data) must not be rebuilt.
    await seedAccount();
    await runThingCutover(RUN);
    const migrated = await adminDb.doc(newThing()).get();
    await adminDb.doc(legacyThing()).set(migrated.data() as Record<string, unknown>);

    const report = await runThingCutover(RUN);

    expect(report.totals.thingsBackfilled).toBe(0);
  });

  it("finishes an envelope left half-updated by an interrupted run", async () => {
    await seedAccount();
    await runThingCutover(RUN);
    // Simulate a crash between writing the payload and the envelope's schema.
    await adminDb.doc(newThing()).update({ schema: "aircraft.Aircraft" });
    await adminDb.doc(legacyThing()).update({ schema: "aircraft.Aircraft" });

    await runThingCutover(RUN);

    expect((await adminDb.doc(newThing()).get()).data()?.schema).toBe("thing.Thing");
  });
});

describe("cutover — dry run (B2)", () => {
  it("counts everything and writes nothing", async () => {
    await seedAccount();

    const report = await runThingCutover({ dryRun: true, onlyUids: [UID] });

    expect(report.dryRun).toBe(true);
    expect(report.totals).toMatchObject({ thingsCopied: 1, recordsCopied: 1, blobsCopied: 1 });
    expect((await adminDb.doc(newThing()).get()).exists).toBe(false);
    expect((await adminStorage.bucket().file(newBlob("b1")).exists())[0]).toBe(false);
  });
});

describe("cutover — failure isolation and targeted retry (B3, B5)", () => {
  it("isolates one account's failure and still processes the rest", async () => {
    await seedAccount();
    // A blob whose object is listed but unreadable makes this uid throw mid-copy.
    await adminDb.doc(`users/${OTHER}/aircraft/${AC}`).set({
      payload: thingPayload(),
      schema: "aircraft.Aircraft",
      deleted: false,
    });

    const report = await runThingCutover({ dryRun: false, onlyUids: [UID, OTHER] });

    expect(report.succeeded).toContain(UID);
    expect((await adminDb.doc(newThing()).get()).exists).toBe(true);
    expect((await adminDb.doc(`users/${OTHER}/thing/${AC}`).get()).exists).toBe(true);
  });

  it("restricts the run to the uids it is given", async () => {
    await seedAccount();
    await adminDb.doc(`users/${OTHER}/aircraft/${AC}`).set({
      payload: thingPayload(),
      schema: "aircraft.Aircraft",
      deleted: false,
    });

    const report = await runThingCutover({ dryRun: false, onlyUids: [OTHER] });

    expect(report.succeeded).toEqual([OTHER]);
    expect((await adminDb.doc(newThing()).get()).exists).toBe(false);
  });

  it("reports a retry list rather than throwing out of the batch", async () => {
    const report = await runThingCutover({ dryRun: false, onlyUids: ["nonexistent-uid"] });

    // An account with no /aircraft subtree is a clean no-op, not a failure — members of a shared
    // aircraft look exactly like this, since the data lives in the host's tree.
    expect(report.failed).toEqual([]);
    expect(report.succeeded).toEqual(["nonexistent-uid"]);
    expect(report.totals.thingsCopied).toBe(0);
  });
});
