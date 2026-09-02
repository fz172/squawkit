import { beforeEach, describe, expect, it } from "vitest";

import { adminDb } from "./helpers.js";

import { ComponentType } from "../src/generated/proto/thing/component_type.js";
import { MaintenanceLog } from "../src/generated/proto/thing/maintenance_log.js";
import { MaintenanceOverview } from "../src/generated/proto/thing/maintenance_overview.js";
import { MaintenanceTask } from "../src/generated/proto/thing/maintenance_task.js";
import {
  backfillLogReadings,
  backfillOverviewCurrent,
  backfillTaskMeters,
  currentReadingsFor,
  runLegacyMeterBackfill,
} from "../src/migration/legacyMeterBackfill.js";

/**
 * The backfill that lets the aviation meter fields be deleted (#761).
 *
 * What is worth testing here is not that values move — it is the three rules that keep the move
 * from destroying anything: never overwrite a keyed value, never clear a legacy one, and put an
 * `EngineHourRule` on the meter its card's component always implied rather than on a guess.
 */

const UID = "legacy-meter-uid";
const THING = "thing-1";

const log = (fields: Partial<MaintenanceLog> = {}): MaintenanceLog =>
  MaintenanceLog.fromPartial({ id: "log-1", ...fields });

const overview = (fields: Partial<MaintenanceOverview> = {}): MaintenanceOverview =>
  MaintenanceOverview.fromPartial({ ...fields });

const task = (fields: Partial<MaintenanceTask> = {}): MaintenanceTask =>
  MaintenanceTask.fromPartial({ id: "task-1", ...fields });

const RUN = { onlyUids: [UID], dryRun: false };

async function seedLog(docId: string, value: MaintenanceLog) {
  await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_log/${docId}`).set({
    payload: Buffer.from(MaintenanceLog.encode(value).finish()).toString("base64"),
    schema: "aircraft.MaintenanceLog",
  });
}

async function seedTask(docId: string, value: MaintenanceTask) {
  await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_task/${docId}`).set({
    payload: Buffer.from(MaintenanceTask.encode(value).finish()).toString("base64"),
    schema: "aircraft.MaintenanceTask",
  });
}

async function seedOverview(value: MaintenanceOverview) {
  await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_overview/current`).set({
    payload: Buffer.from(MaintenanceOverview.encode(value).finish()).toString("base64"),
    schema: "aircraft.MaintenanceOverview",
  });
}

async function readLog(docId: string): Promise<MaintenanceLog> {
  const snap = await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_log/${docId}`).get();
  return MaintenanceLog.decode(Buffer.from(snap.data()!.payload as string, "base64"));
}

async function readTask(docId: string): Promise<MaintenanceTask> {
  const snap = await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_task/${docId}`).get();
  return MaintenanceTask.decode(Buffer.from(snap.data()!.payload as string, "base64"));
}

async function readOverview(): Promise<MaintenanceOverview> {
  const snap = await adminDb
    .doc(`users/${UID}/thing/${THING}/maintenance_overview/current`)
    .get();
  return MaintenanceOverview.decode(Buffer.from(snap.data()!.payload as string, "base64"));
}

describe("backfillLogReadings", () => {
  it("writes one reading per aviation field that has a value", () => {
    const result = backfillLogReadings(
      log({ airframeTime: 1111, engineHour: 1041.8, propTime: 1029.8 }),
    );

    expect(result?.readings).toEqual([
      { meterKey: "airframe_hours", componentId: "", value: 1111 },
      { meterKey: "engine_hours", componentId: "", value: 1041.8 },
      { meterKey: "prop_hours", componentId: "", value: 1029.8 },
    ]);
  });

  it("leaves the legacy fields exactly as they were", () => {
    // Clearing them would break every client older than the release that drops the fallback, and
    // buys nothing: a removed field's bytes decode as unknown and are ignored.
    const result = backfillLogReadings(log({ engineHour: 1041.8 }));

    expect(result?.engineHour).toEqual(1041.8);
  });

  it("treats zero as not recorded rather than as a reading of zero", () => {
    expect(backfillLogReadings(log({ engineHour: 0, airframeTime: 0, propTime: 0 }))).toBeNull();
  });

  it("never overwrites a keyed reading that is already there", () => {
    // A log holding both was written by a build that had `readings`, so the keyed value is the
    // authoritative one — copying the legacy number over it would undo a real edit.
    const result = backfillLogReadings(
      log({
        engineHour: 100,
        readings: [{ meterKey: "engine_hours", componentId: "", value: 250 }],
      }),
    );

    expect(result).toBeNull();
  });

  it("adds only the missing key when a log has some of both", () => {
    const result = backfillLogReadings(
      log({
        engineHour: 100,
        airframeTime: 200,
        readings: [{ meterKey: "engine_hours", componentId: "", value: 250 }],
      }),
    );

    expect(result?.readings).toEqual([
      { meterKey: "engine_hours", componentId: "", value: 250 },
      { meterKey: "airframe_hours", componentId: "", value: 200 },
    ]);
  });
});

describe("currentReadingsFor", () => {
  it("takes the maximum per key across every log", () => {
    expect(
      currentReadingsFor([
        log({ id: "a", engineHour: 100 }),
        log({ id: "b", engineHour: 250 }),
        log({ id: "c", engineHour: 180 }),
      ]),
    ).toEqual([{ meterKey: "engine_hours", componentId: "", value: 250 }]);
  });

  it("counts a keyed meter the aviation fields cannot name", () => {
    // The reason the overview is rebuilt rather than copied: three doubles have nowhere to put an
    // odometer, so copying them would silently drop a car's only meter.
    expect(
      currentReadingsFor([
        log({ readings: [{ meterKey: "odometer", componentId: "", value: 84512 }] }),
      ]),
    ).toEqual([{ meterKey: "odometer", componentId: "", value: 84512 }]);
  });

  it("omits a meter no log has touched rather than reporting zero", () => {
    expect(currentReadingsFor([log({})])).toEqual([]);
  });
});

describe("backfillOverviewCurrent", () => {
  it("rebuilds current from the logs", () => {
    const result = backfillOverviewCurrent(overview({ currentEngineTime: 100 }), [
      log({ engineHour: 250 }),
    ]);

    expect(result?.current).toEqual([
      { meterKey: "engine_hours", componentId: "", value: 250 },
    ]);
  });

  it("prefers the logs over a stale double", () => {
    // The doubles were computed from logs; if the two disagree the logs are the source of truth.
    const result = backfillOverviewCurrent(overview({ currentEngineTime: 9999 }), [
      log({ engineHour: 250 }),
    ]);

    expect(result?.current).toEqual([
      { meterKey: "engine_hours", componentId: "", value: 250 },
    ]);
  });

  it("writes nothing when current already says exactly that", () => {
    const result = backfillOverviewCurrent(
      overview({ current: [{ meterKey: "engine_hours", componentId: "", value: 250 }] }),
      [log({ engineHour: 250 })],
    );

    expect(result).toBeNull();
  });
});

describe("backfillTaskMeters", () => {
  it("puts an airframe card's rule on airframe hours", () => {
    const result = backfillTaskMeters(
      task({
        component: ComponentType.COMPONENT_AIRFRAME,
        rules: [{ engineHourRule: { intervalHours: 100 } }],
      }),
    );

    expect(result?.task.rules[0].meterRule).toEqual({
      meterKey: "airframe_hours",
      interval: 100,
    });
    expect(result?.task.rules[0].engineHourRule).toBeUndefined();
  });

  it("puts every other card's rule on engine hours", () => {
    const result = backfillTaskMeters(
      task({
        component: ComponentType.COMPONENT_ENGINE,
        rules: [{ engineHourRule: { intervalHours: 50 } }],
      }),
    );

    expect(result?.task.rules[0].meterRule).toEqual({ meterKey: "engine_hours", interval: 50 });
  });

  it("leaves a rule that already names its meter alone", () => {
    const result = backfillTaskMeters(
      task({ rules: [{ meterRule: { meterKey: "odometer", interval: 5000 } }] }),
    );

    expect(result).toBeNull();
  });

  it("converts a forced due into the same meter its rules use", () => {
    const result = backfillTaskMeters(
      task({ component: ComponentType.COMPONENT_AIRFRAME, forceDueEngineHour: 1500 }),
    );

    expect(result?.task.forceDueMeter).toEqual({
      meterKey: "airframe_hours",
      componentId: "",
      value: 1500,
    });
    // Left in place, like every other legacy field.
    expect(result?.task.forceDueEngineHour).toEqual(1500);
  });

  it("converts a force-complied status", () => {
    const result = backfillTaskMeters(
      task({ forceCompliedStatus: { compliedEngineHours: 42, compliedMeter: undefined } }),
    );

    expect(result?.task.forceCompliedStatus?.compliedMeter).toEqual({
      meterKey: "engine_hours",
      componentId: "",
      value: 42,
    });
  });

  it("returns null for a task with nothing legacy on it", () => {
    expect(backfillTaskMeters(task({ rules: [{ timeRule: { intervalMonths: 12 } }] }))).toBeNull();
  });
});

describe("runLegacyMeterBackfill", () => {
  beforeEach(async () => {
    await adminDb.recursiveDelete(adminDb.collection(`users/${UID}/thing`));
  });

  it("backfills logs, rebuilds the overview and converts tasks in one pass", async () => {
    await seedLog("l1", log({ id: "l1", engineHour: 100, airframeTime: 120 }));
    await seedLog("l2", log({ id: "l2", engineHour: 250, airframeTime: 300 }));
    await seedOverview(overview({ aircraftId: THING, currentEngineTime: 100 }));
    await seedTask("t1", task({ rules: [{ engineHourRule: { intervalHours: 100 } }] }));

    const report = await runLegacyMeterBackfill(RUN);

    expect(report.logsBackfilled).toEqual(2);
    expect(report.logReadingsWritten).toEqual(4);
    expect(report.overviewsRebuilt).toEqual(1);
    expect(report.tasksBackfilled).toEqual(1);
    expect(report.rulesConverted).toEqual(1);

    expect((await readLog("l1")).readings).toHaveLength(2);
    expect((await readOverview()).current).toEqual([
      { meterKey: "airframe_hours", componentId: "", value: 300 },
      { meterKey: "engine_hours", componentId: "", value: 250 },
    ]);
    expect((await readTask("t1")).rules[0].meterRule?.meterKey).toEqual("engine_hours");
  });

  it("rebuilds the overview from the logs as backfilled, not as found", async () => {
    // The ordering that matters: logs are fixed first, so the overview sees the same values a
    // client would after this runs.
    //
    // `aircraftId` is set only so the encoded payload is non-empty: an all-default proto encodes to
    // zero bytes, and `payloadBytes` reads an empty payload as "nothing decodable" rather than as
    // an empty record. Real overviews always carry counts, so this is a fixture concern.
    await seedLog("l1", log({ id: "l1", engineHour: 250 }));
    await seedOverview(overview({ aircraftId: THING }));

    await runLegacyMeterBackfill(RUN);

    expect((await readOverview()).current).toEqual([
      { meterKey: "engine_hours", componentId: "", value: 250 },
    ]);
  });

  it("writes nothing in a dry run", async () => {
    await seedLog("l1", log({ id: "l1", engineHour: 100 }));

    const report = await runLegacyMeterBackfill({ ...RUN, dryRun: true });

    expect(report.logsBackfilled).toEqual(1);
    expect((await readLog("l1")).readings).toEqual([]);
  });

  it("is idempotent", async () => {
    await seedLog("l1", log({ id: "l1", engineHour: 100 }));
    await seedTask("t1", task({ forceDueEngineHour: 1500 }));
    await seedOverview(overview({ aircraftId: THING }));

    await runLegacyMeterBackfill(RUN);
    const second = await runLegacyMeterBackfill(RUN);

    expect(second.logsBackfilled).toEqual(0);
    expect(second.tasksBackfilled).toEqual(0);
    expect(second.overviewsRebuilt).toEqual(0);
  });

  it("reports an undecodable payload rather than guessing at it", async () => {
    await adminDb.doc(`users/${UID}/thing/${THING}/maintenance_log/broken`).set({ payload: "" });

    const report = await runLegacyMeterBackfill(RUN);

    expect(report.undecodable).toEqual([
      { uid: UID, thingId: THING, kind: "maintenance_log", docId: "broken" },
    ]);
  });
});
