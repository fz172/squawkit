import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { Thing } from "../src/generated/proto/thing/thing.js";
import {
  restructureAirplaneTree,
  runAirplaneTreeMigration,
} from "../src/migration/airplaneTreeMigration.js";

/**
 * The one-time repair of airplane component trees (#729).
 *
 * Tested harder than most migrations because it rewrites the component tree of every aeroplane in
 * production and re-derives every component id. Getting it wrong loses engine serials owners cannot
 * re-enter from memory.
 */

const UID = "airplane-tree-uid";
const THING = "thing-1";

const slot = (
  slotKey: string,
  fields: Partial<{ make: string; model: string; serial: string }> = {},
  children: ReturnType<typeof slot>[] = [],
) => ({
  id: "",
  slotKey,
  make: fields.make ?? "",
  model: fields.model ?? "",
  serial: fields.serial ?? "",
  children,
});

/** The DNA a Thing carries from before the restructure — the slots the cutover matched. */
function legacyTemplate() {
  return {
    id: "airplane",
    version: 1,
    minAppVersion: 0,
    displayName: "Airplane",
    icon: "airplane",
    sortOrder: 0,
    specFields: [],
    meters: [],
    starterTasks: [],
    componentSlots: [
      {
        slotKey: "airframe",
        label: "Airframe",
        repeatable: false,
        serialExpected: true,
        specKeys: [],
        inlineWithParent: false,
        compactFields: false,
        children: [],
      },
    ],
  };
}

/** The shape Phase 1's cutover left behind. */
function legacyThing(): Thing {
  return Thing.fromPartial({
    id: THING,
    name: "N123AB",
    template: legacyTemplate(),
    components: [
      slot("airframe", { make: "Cessna", model: "172", serial: "AF-1" }, [
        slot("engine", { make: "Lycoming", model: "O-320", serial: "E-1" }, [
          slot("propeller", {}, [
            slot("hub", { make: "McCauley", model: "1C160", serial: "H-1" }),
            slot("blade", { serial: "B-1" }),
            slot("blade", { serial: "B-2" }),
          ]),
        ]),
        slot("engine", { make: "Lycoming", model: "O-320", serial: "E-2" }, [
          slot("propeller", {}, [slot("hub", { serial: "H-2" })]),
        ]),
      ]),
    ],
  });
}

async function seed(thing: Thing) {
  await adminDb.doc(`users/${UID}/thing/${THING}`).set({
    payload: Buffer.from(Thing.encode(thing).finish()).toString("base64"),
    schema: "thing.Thing",
  });
}

async function storedThing(): Promise<Thing> {
  const snap = await adminDb.doc(`users/${UID}/thing/${THING}`).get();
  const payload = snap.data()?.payload as string;
  return Thing.decode(new Uint8Array(Buffer.from(payload, "base64")));
}

const RUN = { dryRun: false, onlyUids: [UID] };

describe("airplane tree migration", () => {
  beforeEach(async () => {
    await adminDb.recursiveDelete(adminDb.collection(`users/${UID}/thing`));
  });

  it("lifts engines out of the airframe wrapper", () => {
    const result = restructureAirplaneTree(legacyThing());

    expect(result).not.toBeNull();
    expect(result!.components.map((c) => c.slotKey)).toEqual(["engine", "engine"]);
    expect(result!.enginesLifted).toBe(2);
    // The engines' own identity survives the lift.
    expect(result!.components.map((c) => c.serial)).toEqual(["E-1", "E-2"]);
  });

  it("folds the hub's identity into the propeller and keeps the blades", () => {
    const result = restructureAirplaneTree(legacyThing())!;

    const propeller = result.components[0].children[0];
    expect(propeller.slotKey).toBe("propeller");
    expect(propeller.make).toBe("McCauley");
    expect(propeller.model).toBe("1C160");
    expect(propeller.serial).toBe("H-1");
    expect(propeller.children.map((c) => c.serial)).toEqual(["B-1", "B-2"]);
    expect(result.hubsFolded).toBe(2);
  });

  it("never overwrites a value the propeller already carries", () => {
    // A Thing edited on a new build has its own propeller identity; the hub's is the stale copy.
    const thing = legacyThing();
    thing.components[0].children[0].children[0].make = "Hartzell";
    thing.components[0].children[0].children[0].serial = "P-NEW";

    const propeller = restructureAirplaneTree(thing)!.components[0].children[0];

    expect(propeller.make).toBe("Hartzell");
    expect(propeller.serial).toBe("P-NEW");
    // The half it had nothing for still comes from the hub.
    expect(propeller.model).toBe("1C160");
  });

  it("re-derives every component id from its new path", () => {
    // The ids encode the path, so lifting an engine changes them. Nothing joins on them yet, which
    // is what makes this safe — PRD §4.3's component_id migration has to come after this one.
    const result = restructureAirplaneTree(legacyThing())!;

    expect(result.components[0].id).toBe(`${THING}:engine.0`);
    expect(result.components[1].id).toBe(`${THING}:engine.1`);
    expect(result.components[0].children[0].id).toBe(`${THING}:engine.0.propeller.0`);
    expect(result.components[0].children[0].children[1].id).toBe(
      `${THING}:engine.0.propeller.0.blade.1`,
    );
  });

  it("leaves an already-migrated thing alone", () => {
    const migrated = Thing.fromPartial({
      id: THING,
      components: [slot("engine", { serial: "E-1" }, [slot("propeller", { serial: "P-1" })])],
    });

    expect(restructureAirplaneTree(migrated)).toBeNull();
  });

  it("writes the restructured tree and the matching slots", async () => {
    await seed(legacyThing());

    const report = await runAirplaneTreeMigration(RUN);

    expect(report.migrated).toHaveLength(1);
    const stored = await storedThing();
    expect(stored.components.map((c) => c.slotKey)).toEqual(["engine", "engine"]);
    // The DNA has to move with the components: it is what the client walks, so restructuring the
    // tree alone would leave a Thing the app still could not read.
    expect(stored.template?.componentSlots.map((s) => s.slotKey)).toEqual(["engine"]);
    expect(stored.template?.componentSlots[0].children[0].slotKey).toBe("propeller");
    expect(stored.template?.componentSlots[0].children[0].children[0].slotKey).toBe("blade");
  });

  it("leaves the DNA citing the version whose slots it just wrote", async () => {
    await seed(legacyThing());

    await runAirplaneTreeMigration(RUN);

    // The whole template moves, not just the slots. Writing the current slots under the version the
    // Thing already claimed would produce two aeroplanes citing one version while walking different
    // trees — the #732 blade regression, reintroduced by the migration meant to prevent it.
    const stored = await storedThing();
    expect(stored.template?.id).toBe("airplane");
    expect(stored.template?.version).toBeGreaterThan(1);
    expect(stored.template?.minAppVersion).toBe(0);
  });

  it("does not invent DNA for a thing that carries none", async () => {
    // A Thing with no template resolves through the baked-in fallback, which already declares the
    // new slots. Writing DNA here would freeze a copy of them into user data for no reason.
    const thing = legacyThing();
    thing.template = undefined;
    await seed(thing);

    await runAirplaneTreeMigration(RUN);

    const stored = await storedThing();
    expect(stored.template).toBeUndefined();
    // Its components are still repaired — that is what the fallback slots have to match.
    expect(stored.components.map((c) => c.slotKey)).toEqual(["engine", "engine"]);
  });

  it("writes nothing on a dry run", async () => {
    await seed(legacyThing());

    const report = await runAirplaneTreeMigration({ ...RUN, dryRun: true });

    expect(report.migrated).toHaveLength(1);
    expect((await storedThing()).components[0].slotKey).toBe("airframe");
  });

  it("is idempotent", async () => {
    await seed(legacyThing());

    await runAirplaneTreeMigration(RUN);
    const afterFirst = await storedThing();
    const second = await runAirplaneTreeMigration(RUN);

    expect(second.migrated).toHaveLength(0);
    expect(second.alreadyMigrated).toBe(1);
    expect(await storedThing()).toEqual(afterFirst);
  });

  it("reports an undecodable payload rather than guessing", async () => {
    await adminDb.doc(`users/${UID}/thing/${THING}`).set({ payload: "" });

    const report = await runAirplaneTreeMigration(RUN);

    expect(report.undecodable).toEqual([{ uid: UID, thingId: THING }]);
    expect(report.migrated).toHaveLength(0);
  });
});
