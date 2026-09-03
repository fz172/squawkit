import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { Thing } from "../src/generated/proto/thing/thing.js";
import { ThingTemplate } from "../src/generated/proto/thing/template.js";
import {
  loadCanonicalTemplates,
  runThingDnaRefresh,
} from "../src/migration/thingDnaRefresh.js";

/**
 * Refreshing a Thing's frozen DNA to the preset this repo ships (#732).
 *
 * The regression that prompted it: an aeroplane created before `compact_instances` existed drew its
 * propeller blades as a flat list, because the client walks the template stored inside the Thing and
 * never the pool. So the assertions that matter are about the SLOTS reaching storage, not just the
 * version number moving.
 */

const UID = "dna-refresh-uid";
const THING = "thing-1";

const RUN = { dryRun: false, onlyUids: [UID] };

/** The airplane DNA a Thing froze before #732: same slots, no `compact_instances`. */
function staleAirplaneTemplate(): ThingTemplate {
  return ThingTemplate.fromPartial({
    id: "airplane",
    version: 1,
    minAppVersion: 0,
    displayName: "Airplane",
    icon: "airplane",
    componentSlots: [
      {
        slotKey: "engine",
        label: "Engine",
        repeatable: true,
        serialExpected: true,
        compactFields: true,
        children: [
          {
            slotKey: "propeller",
            label: "Propeller",
            serialExpected: true,
            inlineWithParent: true,
            compactFields: true,
            children: [
              {
                slotKey: "blade",
                label: "Blade",
                repeatable: true,
                serialExpected: true,
                specKeys: ["serial"],
                inlineWithParent: true,
                compactFields: true,
                // The field the regression is about: absent here, present in the canonical asset.
                children: [],
              },
            ],
          },
        ],
      },
    ],
  });
}

/** `null` means a Thing carrying no DNA — `undefined` would take the default. */
function airplane(template: ThingTemplate | null = staleAirplaneTemplate()): Thing {
  return Thing.fromPartial({
    id: THING,
    name: "N123AB",
    template: template ?? undefined,
    components: [
      {
        id: `${THING}:engine.0`,
        slotKey: "engine",
        make: "Lycoming",
        serial: "E-1",
        children: [
          {
            id: `${THING}:engine.0.propeller.0`,
            slotKey: "propeller",
            serial: "P-1",
            children: [
              { id: `${THING}:engine.0.propeller.0.blade.0`, slotKey: "blade", serial: "B-1" },
              { id: `${THING}:engine.0.propeller.0.blade.1`, slotKey: "blade", serial: "B-2" },
            ],
          },
        ],
      },
    ],
  });
}

async function seed(thing: Thing, id = THING) {
  await adminDb.doc(`users/${UID}/thing/${id}`).set({
    payload: Buffer.from(Thing.encode(thing).finish()).toString("base64"),
    schema: "thing.Thing",
  });
}

async function storedThing(id = THING): Promise<Thing> {
  const snap = await adminDb.doc(`users/${UID}/thing/${id}`).get();
  return Thing.decode(new Uint8Array(Buffer.from(snap.data()?.payload as string, "base64")));
}

function bladeSlot(template: ThingTemplate | undefined) {
  return template?.componentSlots[0].children[0].children[0];
}

describe("canonical template assets", () => {
  it("loads every shipped preset, keyed by the id in its own bytes", () => {
    const pool = loadCanonicalTemplates();

    // The six of templates/README.md. Keyed by the decoded id, so a mis-named .pb cannot become the
    // canonical answer for the wrong preset.
    expect([...pool.keys()].sort()).toEqual([
      "airplane",
      "automotive",
      "bike",
      "boat",
      "custom",
      "home",
    ]);
    for (const [id, template] of pool) expect(template.id).toBe(id);
  });

  it("carries the blade slot the regression was about", () => {
    // If this is ever false the migration is a no-op that still reports success, which is the one
    // failure a count of written documents cannot show.
    expect(bladeSlot(loadCanonicalTemplates().get("airplane"))?.compactInstances).toBe(true);
  });
});

describe("thing dna refresh", () => {
  beforeEach(async () => {
    await adminDb.recursiveDelete(adminDb.collection(`users/${UID}/thing`));
  });

  it("replaces stale DNA with the canonical bytes", async () => {
    await seed(airplane());

    const report = await runThingDnaRefresh(RUN);

    expect(report.refreshed).toHaveLength(1);
    expect(report.refreshed[0]).toMatchObject({ templateId: "airplane", fromVersion: 1 });
    const stored = await storedThing();
    // The point of the whole exercise: the blades now say they are a set.
    expect(bladeSlot(stored.template)?.compactInstances).toBe(true);
    expect(stored.template?.version).toBe(report.refreshed[0].toVersion);
  });

  it("leaves the components exactly as stored", async () => {
    await seed(airplane());

    await runThingDnaRefresh(RUN);

    // A template refresh, not a tree repair — the two migrations are complementary, and this one
    // must never touch a serial an owner cannot re-enter from memory.
    const stored = await storedThing();
    expect(stored.components[0].serial).toBe("E-1");
    const propeller = stored.components[0].children[0];
    expect(propeller.serial).toBe("P-1");
    expect(propeller.children.map((c) => c.serial)).toEqual(["B-1", "B-2"]);
    expect(stored.name).toBe("N123AB");
  });

  it("writes nothing on a dry run", async () => {
    await seed(airplane());

    const report = await runThingDnaRefresh({ ...RUN, dryRun: true });

    expect(report.refreshed).toHaveLength(1);
    expect(bladeSlot((await storedThing()).template)?.compactInstances).toBe(false);
  });

  it("skips a tombstone, and does not restart its retention clock", async () => {
    // A deleted record is waiting on the storage sweep's hard delete. The write would stamp
    // `lastUpdateTimestamp` — the field the sweep ages it by — and buy it another 30 days.
    await seed(airplane());
    await adminDb.doc(`users/${UID}/thing/${THING}`).update({ deleted: true });
    const before = (await adminDb.doc(`users/${UID}/thing/${THING}`).get()).data();

    const report = await runThingDnaRefresh(RUN);

    expect(report.skippedTombstones).toBe(1);
    expect(report.refreshed).toHaveLength(0);
    const after = (await adminDb.doc(`users/${UID}/thing/${THING}`).get()).data();
    expect(after?.payload).toBe(before?.payload);
    expect(after?.lastUpdateTimestamp).toEqual(before?.lastUpdateTimestamp);
  });

  it("skips a thing already carrying the canonical bytes", async () => {
    await seed(airplane(loadCanonicalTemplates().get("airplane")));

    const report = await runThingDnaRefresh(RUN);

    expect(report.refreshed).toHaveLength(0);
    expect(report.alreadyCurrent).toBe(1);
  });

  it("is idempotent", async () => {
    await seed(airplane());

    await runThingDnaRefresh(RUN);
    const second = await runThingDnaRefresh(RUN);

    // Re-running after a partial failure should cost reads and nothing else.
    expect(second.refreshed).toHaveLength(0);
    expect(second.alreadyCurrent).toBe(1);
  });

  it("leaves a template id naming no shipped preset alone", async () => {
    // The shape a forked template will have once #727 lands: refresh what still matches a preset,
    // never overwrite something the pool has no opinion about.
    const forked = staleAirplaneTemplate();
    forked.id = "airplane-mine";
    await seed(airplane(forked));

    const report = await runThingDnaRefresh(RUN);

    expect(report.refreshed).toHaveLength(0);
    expect(report.unknownTemplateId).toEqual([
      { uid: UID, thingId: THING, templateId: "airplane-mine" },
    ]);
    expect((await storedThing()).template?.id).toBe("airplane-mine");
  });

  it("leaves a thing carrying no DNA alone by default", async () => {
    // It resolves through the build's own fallback, so it is never stale. Freezing a copy into it
    // would trade that for a snapshot every future bump has to migrate.
    await seed(airplane(null));

    const report = await runThingDnaRefresh(RUN);

    expect(report.leftWithoutDna).toBe(1);
    expect(report.inflated).toHaveLength(0);
    expect((await storedThing()).template).toBeUndefined();
  });

  it("inflates a thing carrying no DNA when asked", async () => {
    await seed(airplane(null));

    const report = await runThingDnaRefresh({ ...RUN, inflateMissing: true });

    expect(report.inflated).toHaveLength(1);
    // Absent DNA can only mean a Thing that predates templates, and every one of those is an
    // aeroplane — thing.proto reserves fields 7 and 8 on exactly that reasoning.
    expect(report.inflated[0]).toMatchObject({ templateId: "airplane", fromVersion: null });
    expect((await storedThing()).template?.id).toBe("airplane");
  });

  it("restricts to the presets named", async () => {
    const car = staleAirplaneTemplate();
    car.id = "automotive";
    await seed(airplane(), "plane");
    await seed(airplane(car), "car");

    const report = await runThingDnaRefresh({ ...RUN, onlyTemplateIds: ["airplane"] });

    expect(report.refreshed.map((t) => t.thingId)).toEqual(["plane"]);
    expect(report.filteredOut).toBe(1);
  });

  it("reports a payload it cannot decode rather than guessing", async () => {
    await adminDb.doc(`users/${UID}/thing/broken`).set({ payload: "", schema: "thing.Thing" });

    const report = await runThingDnaRefresh(RUN);

    expect(report.undecodable).toEqual([{ uid: UID, thingId: "broken" }]);
  });
});
