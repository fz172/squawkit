import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { Component } from "../src/generated/proto/thing/component.js";
import { Spec } from "../src/generated/proto/thing/spec.js";
import { Thing } from "../src/generated/proto/thing/thing.js";
import { runThingDnaAudit } from "../src/migration/thingDnaAudit.js";

/**
 * The DNA audit, against the emulator.
 *
 * The audit exists so #718 is written against a known population rather than a hypothetical one —
 * so what these tests actually protect is the *count being trustworthy*. A miscount in either
 * direction is worse than no audit: too high and a migration gets written for Things that do not
 * need it; too low and the field removal (#668) proceeds over data that has nowhere else to live.
 *
 * Payloads are seeded as **base64 strings**, the shape `FirestoreSyncWriter` actually writes.
 * Seeding Buffers instead is what let #428 ship: every test passed against a decoder that could not
 * read a real document.
 */

const UID = "user-dna-audit";
const OTHER = "user-dna-audit-2";

const b64 = (bytes: Uint8Array) => Buffer.from(bytes).toString("base64");

const thingDoc = (uid: string, id: string) => `users/${uid}/thing/${id}`;

function payload(overrides: Partial<Parameters<typeof Thing.fromPartial>[0]> = {}) {
  return b64(
    Thing.encode(
      Thing.fromPartial({ id: "t", make: "Cessna", model: "172", ...overrides }),
    ).finish(),
  );
}

/** A Thing the cutover migrated: spec and components present. */
const migrated = (id: string) =>
  payload({
    id,
    spec: [Spec.fromPartial({ key: "make", value: "Cessna" })],
    components: [Component.fromPartial({ id: `${id}:airframe.0`, slotKey: "airframe" })],
  });

/** A Thing the client wrote after the cutover: neither field ever populated. */
const uninflated = (id: string) => payload({ id });

async function seed(path: string, value: unknown) {
  await adminDb.doc(path).set({ payload: value, schema: "thing.Thing" });
}

describe("thing DNA audit", () => {
  beforeEach(async () => {
    for (const uid of [UID, OTHER]) {
      const docs = await adminDb.collection(`users/${uid}/thing`).listDocuments();
      await Promise.all(docs.map((doc) => doc.delete()));
      await adminDb.doc(`users/${uid}`).set({ seeded: true });
    }
  });

  it("reports nothing when every Thing was migrated", async () => {
    await seed(thingDoc(UID, "a"), migrated("a"));

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    expect(report.needingDna).toHaveLength(0);
    expect(report.scannedThings).toBe(1);
  });

  it("finds a Thing the client wrote after the cutover", async () => {
    await seed(thingDoc(UID, "a"), migrated("a"));
    await seed(thingDoc(UID, "b"), uninflated("b"));

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    expect(report.needingDna.map((entry) => entry.thingId)).toEqual(["b"]);
    expect(report.scannedThings).toBe(2);
  });

  it("keys off components, not spec", async () => {
    // A Thing with no make, model, serial or tail number legitimately has an empty spec — the
    // backend drops empty values rather than storing blanks. Treating that as needing repair would
    // count Things that are already correct.
    await seed(
      thingDoc(UID, "blank"),
      payload({
        id: "blank",
        make: "",
        model: "",
        components: [Component.fromPartial({ id: "blank:airframe.0", slotKey: "airframe" })],
      }),
    );

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    expect(report.needingDna).toHaveLength(0);
  });

  it("counts a migrated Thing without DNA separately, not as a backfill target", async () => {
    // The cutover predates field 12, so a migrated Thing has components but no template. Absent DNA
    // resolves to airplane over a closed set and #717 fills it on the next write — it is not
    // something a backfill has to repair, and conflating the two would inflate the number.
    await seed(thingDoc(UID, "a"), migrated("a"));

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    expect(report.needingDna).toHaveLength(0);
    expect(report.missingTemplateOnly.map((entry) => entry.thingId)).toEqual(["a"]);
  });

  it("reports an undecodable payload rather than counting it either way", async () => {
    await seed(thingDoc(UID, "bad"), "not-valid-base64-proto!!");

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    // Not silently treated as needing repair, and not silently passed over.
    expect(report.needingDna).toHaveLength(0);
    expect(report.undecodable.map((entry) => entry.thingId)).toEqual(["bad"]);
  });

  it("scopes to the accounts asked for", async () => {
    await seed(thingDoc(UID, "a"), uninflated("a"));
    await seed(thingDoc(OTHER, "b"), uninflated("b"));

    const report = await runThingDnaAudit({ onlyUids: [UID] });

    expect(report.scannedUids).toBe(1);
    expect(report.needingDna.map((entry) => entry.uid)).toEqual([UID]);
  });

  it("writes nothing", async () => {
    await seed(thingDoc(UID, "a"), uninflated("a"));
    const before = (await adminDb.doc(thingDoc(UID, "a")).get()).data();

    await runThingDnaAudit({ onlyUids: [UID] });

    const after = (await adminDb.doc(thingDoc(UID, "a")).get()).data();
    expect(after).toEqual(before);
  });
});
