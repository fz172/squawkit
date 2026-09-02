import { FieldValue } from "firebase-admin/firestore";

import { adminDb } from "../config/firebaseAdmin.js";
import { ENTITY_SEGMENTS, type EntitySegment } from "../config/entitySegment.js";
import { ComponentType } from "../generated/proto/thing/component_type.js";
import { MaintenanceLog } from "../generated/proto/thing/maintenance_log.js";
import { MaintenanceOverview } from "../generated/proto/thing/maintenance_overview.js";
import { MaintenanceTask } from "../generated/proto/thing/maintenance_task.js";
import type { MeterReading } from "../generated/proto/thing/meter_reading.js";
import { payloadBytes, type SyncDocWire } from "../shared/syncDocWire.js";

/**
 * Writes the keyed meter form onto every record that still carries only the aviation one (#761).
 *
 * ## Why this has to exist before the fields can go
 *
 * Six fields were superseded by `MeterReading` in #730 and #759, and every reader was moved onto a
 * helper that prefers the keyed form and falls back to the legacy one:
 *
 * | legacy | superseded by |
 * |---|---|
 * | `MaintenanceLog.engine_hour` / `airframe_time` / `prop_time` | `readings` |
 * | `MaintenanceOverview.current_airframe_time` / `current_engine_time` / `current_propeller_time` | `current` |
 * | `MaintenanceTask.force_due_engine_hour` | `force_due_meter` |
 * | `ForceCompliedStatus.complied_engine_hours` | `complied_meter` |
 * | `InspectionRule.engine_hour_rule` | `meter_rule` |
 *
 * The fallback is what lets the app read years of existing history, and it is also the thing that
 * cannot be removed while any record still needs it. **Deleting the fields without this is what
 * would make an aeroplane with a decade of logs report zero hours flown.** So the ordering is:
 * this runs, then a release drops the fallbacks, then the fields come out of the protos.
 *
 * ## What it does NOT do
 *
 * **It never clears a legacy field.** Only the keyed form is added. Clearing would break every
 * client older than the release that drops the fallback, for no benefit: once a field is removed
 * from the schema, bytes still carrying its number decode as unknown fields and are ignored. The
 * legacy values simply stop being read, and then stop being written, and the stored bytes are left
 * alone forever.
 *
 * **It never overwrites a keyed value that is already there.** A record holding both was written by
 * a build that had the keyed form, so its keyed value is the authoritative one — copying the legacy
 * number over it would undo a real edit.
 *
 * ## The overview is recomputed, not copied
 *
 * `current` is *defined* as the maximum reading per meter across the Thing's logs, which is what
 * the three doubles always held. Copying them would faithfully reproduce a value that was computed
 * from logs which this same pass is fixing, and would miss any meter the aviation fields cannot
 * name — an odometer, say. So the overview is rebuilt from the logs after they are backfilled,
 * which is both more correct and the same computation `MeterReadings.currentReadings` performs on
 * the client.
 *
 * ## Idempotent, and safe to re-run
 *
 * Every conversion is "add the keyed form if the legacy one has a value and the keyed one does
 * not". A second run finds nothing to do and writes nothing, so re-running after a partial failure
 * repairs only what is left.
 */

export type LegacyMeterBackfillOptions = {
  /** Restrict to these accounts. Empty or absent migrates every account. */
  onlyUids?: readonly string[];
  /** Report what would change and write nothing. */
  dryRun: boolean;
};

export type LegacyMeterBackfillReport = {
  dryRun: boolean;
  scannedUids: number;
  scannedThings: number;
  scannedLogs: number;
  scannedTasks: number;
  scannedOverviews: number;
  /** Logs that gained at least one `readings` entry. */
  logsBackfilled: number;
  /** Individual `MeterReading`s written onto logs. */
  logReadingsWritten: number;
  /** Overviews whose `current` was rebuilt from their Thing's logs. */
  overviewsRebuilt: number;
  /** Tasks that gained a `meter_rule`, a `force_due_meter` or a `complied_meter`. */
  tasksBackfilled: number;
  rulesConverted: number;
  forcedDuesConverted: number;
  compliedStatusesConverted: number;
  /** Payloads that would not decode. Reported, never guessed at. */
  undecodable: Array<{ uid: string; thingId: string; kind: string; docId: string }>;
  elapsedMs: number;
};

/** Collection segments, from `CollectionKind.wireName`. Stored data — never rename. */
const KIND_LOG = "maintenance_log";
const KIND_TASK = "maintenance_task";
const KIND_OVERVIEW = "maintenance_overview";

/** From `MeterKeys`. The three the aviation fields can name, and the only ones they ever meant. */
const AIRFRAME_HOURS = "airframe_hours";
const ENGINE_HOURS = "engine_hours";
const PROP_HOURS = "prop_hours";

/**
 * The meter a legacy field on [component] meant.
 *
 * Transcribed from `MeterRules.defaultMeterKey`: `EngineHourRule` named its meter in its *type*
 * rather than carrying it, so which one it meant came from the card's component — an airframe task
 * tracked airframe time, everything else engine hours. Getting this wrong silently re-points a
 * schedule at the wrong meter, so `legacy-meter-backfill.test.ts` asserts both branches.
 */
function defaultMeterKey(component: ComponentType): string {
  return component === ComponentType.COMPONENT_AIRFRAME ? AIRFRAME_HOURS : ENGINE_HOURS;
}

/** A reading for [meterKey] already present on [readings], if any. */
function hasReading(readings: MeterReading[], meterKey: string): boolean {
  return readings.some((r) => r.meterKey === meterKey);
}

function reading(meterKey: string, value: number): MeterReading {
  // `component_id` stays empty: the legacy fields never named one, and `readingFor` matches on the
  // key alone. Inventing a component here would claim a precision the source data does not have.
  return { meterKey, componentId: "", value };
}

/**
 * [log] with a `readings` entry for each legacy field that has a value and no keyed form yet, or
 * null when there is nothing to add.
 */
export function backfillLogReadings(log: MaintenanceLog): MaintenanceLog | null {
  const additions: MeterReading[] = [];
  const legacy: Array<[string, number]> = [
    [AIRFRAME_HOURS, log.airframeTime],
    [ENGINE_HOURS, log.engineHour],
    [PROP_HOURS, log.propTime],
  ];
  for (const [key, value] of legacy) {
    // `> 0` matches `readingFor`, which treats zero as "not recorded" rather than "reads zero".
    if (value > 0 && !hasReading(log.readings, key)) additions.push(reading(key, value));
  }
  if (additions.length === 0) return null;
  return { ...log, readings: [...log.readings, ...additions] };
}

/**
 * The current reading for every meter [logs] carry — the maximum per key.
 *
 * Transcribed from `MeterReadings.currentReadings`, including its fallback: a log that only has the
 * legacy fields still contributes, which is what lets this run before or after the log backfill and
 * produce the same answer.
 */
export function currentReadingsFor(logs: MaintenanceLog[]): MeterReading[] {
  const valueFor = (log: MaintenanceLog, key: string): number | null => {
    const keyed = log.readings.find((r) => r.meterKey === key);
    if (keyed != null) return keyed.value > 0 ? keyed.value : null;
    const legacy =
      key === AIRFRAME_HOURS
        ? log.airframeTime
        : key === ENGINE_HOURS
          ? log.engineHour
          : key === PROP_HOURS
            ? log.propTime
            : 0;
    return legacy > 0 ? legacy : null;
  };

  const keys = new Set<string>();
  for (const log of logs) {
    for (const r of log.readings) keys.add(r.meterKey);
    if (log.airframeTime > 0) keys.add(AIRFRAME_HOURS);
    if (log.engineHour > 0) keys.add(ENGINE_HOURS);
    if (log.propTime > 0) keys.add(PROP_HOURS);
  }

  const out: MeterReading[] = [];
  for (const key of [...keys].sort()) {
    let max: number | null = null;
    for (const log of logs) {
      const value = valueFor(log, key);
      if (value != null && (max == null || value > max)) max = value;
    }
    // Absent rather than zero, so a reader can tell "not recorded yet" from "reads zero".
    if (max != null) out.push(reading(key, max));
  }
  return out;
}

/**
 * [overview] with `current` rebuilt from [logs], or null when it already says exactly that.
 *
 * Rebuilt rather than copied from the three doubles — see the module header.
 */
export function backfillOverviewCurrent(
  overview: MaintenanceOverview,
  logs: MaintenanceLog[],
): MaintenanceOverview | null {
  const rebuilt = currentReadingsFor(logs);
  const same =
    rebuilt.length === overview.current.length &&
    rebuilt.every((r) => {
      const existing = overview.current.find((c) => c.meterKey === r.meterKey);
      return existing != null && existing.value === r.value;
    });
  if (same) return null;
  return { ...overview, current: rebuilt };
}

export type TaskBackfillCounts = {
  rulesConverted: number;
  forcedDueConverted: boolean;
  compliedConverted: boolean;
};

/**
 * [task] with every legacy meter field expressed in its keyed form, or null when there is nothing
 * to convert.
 */
export function backfillTaskMeters(
  task: MaintenanceTask,
): { task: MaintenanceTask; counts: TaskBackfillCounts } | null {
  const key = defaultMeterKey(task.component);
  let rulesConverted = 0;

  const rules = task.rules.map((rule) => {
    // A rule already carrying a keyed form is left exactly as it is, `oneof` and all: converting it
    // would clear whichever branch is set and could only lose information.
    if (rule.meterRule != null) return rule;
    const engine = rule.engineHourRule;
    if (engine == null || engine.intervalHours <= 0) return rule;
    rulesConverted++;
    // A `oneof` holds one branch, so this replaces rather than adds — which is the point. The
    // interval and the meaning are identical; only the naming of the meter becomes explicit.
    return { ...rule, engineHourRule: undefined, meterRule: { meterKey: key, interval: engine.intervalHours } };
  });

  const forcedDueConverted =
    task.forceDueMeter == null && task.forceDueEngineHour > 0;
  const forceDueMeter = forcedDueConverted
    ? reading(key, task.forceDueEngineHour)
    : task.forceDueMeter;

  const complied = task.forceCompliedStatus;
  const compliedConverted =
    complied != null && complied.compliedMeter == null && complied.compliedEngineHours > 0;
  const forceCompliedStatus =
    complied != null && compliedConverted
      ? { ...complied, compliedMeter: reading(key, complied.compliedEngineHours) }
      : complied;

  if (rulesConverted === 0 && !forcedDueConverted && !compliedConverted) return null;

  return {
    task: { ...task, rules, forceDueMeter, forceCompliedStatus },
    counts: { rulesConverted, forcedDueConverted, compliedConverted },
  };
}

async function allUids(): Promise<string[]> {
  // listDocuments, not a query: a `users/{uid}` document carries no fields of its own, only
  // subcollections, and a fieldless document is "missing" to `.get()`.
  const refs = await adminDb.collection("users").listDocuments();
  return refs.map((ref) => ref.id);
}

/** Decodes one synced document, or records it as undecodable and returns null. */
function decode<T>(
  wire: SyncDocWire,
  decoder: { decode: (bytes: Uint8Array) => T },
): T | null {
  const bytes = payloadBytes(wire.payload);
  if (bytes == null) return null;
  try {
    return decoder.decode(bytes);
  } catch {
    // The same refusal every migration here makes: report it rather than assume a shape for bytes
    // we cannot read.
    return null;
  }
}

function encoded(bytes: Uint8Array): string {
  return Buffer.from(bytes).toString("base64");
}

export async function runLegacyMeterBackfill(
  options: LegacyMeterBackfillOptions,
): Promise<LegacyMeterBackfillReport> {
  const startedAtMs = Date.now();
  const uids =
    options.onlyUids != null && options.onlyUids.length > 0
      ? [...options.onlyUids]
      : await allUids();

  const undecodable: LegacyMeterBackfillReport["undecodable"] = [];
  let scannedThings = 0;
  let scannedLogs = 0;
  let scannedTasks = 0;
  let scannedOverviews = 0;
  let logsBackfilled = 0;
  let logReadingsWritten = 0;
  let overviewsRebuilt = 0;
  let tasksBackfilled = 0;
  let rulesConverted = 0;
  let forcedDuesConverted = 0;
  let compliedStatusesConverted = 0;

  for (const uid of uids) {
    // Both segments: an account still on the pre-Thing paths has the same records under
    // `aircraft`, and skipping it would leave exactly the population this exists to repair.
    for (const segment of ENTITY_SEGMENTS as readonly EntitySegment[]) {
      const things = await adminDb.collection(`users/${uid}/${segment}`).listDocuments();

      for (const thingRef of things) {
        scannedThings++;
        const thingId = thingRef.id;

        // ── Logs, first: the overview below is rebuilt from what they end up holding. ──
        const logSnap = await thingRef.collection(KIND_LOG).get();
        const logs: MaintenanceLog[] = [];
        for (const doc of logSnap.docs) {
          scannedLogs++;
          const log = decode(doc.data() as SyncDocWire, MaintenanceLog);
          if (log == null) {
            undecodable.push({ uid, thingId, kind: KIND_LOG, docId: doc.id });
            continue;
          }
          const backfilled = backfillLogReadings(log);
          if (backfilled == null) {
            logs.push(log);
            continue;
          }
          logsBackfilled++;
          logReadingsWritten += backfilled.readings.length - log.readings.length;
          logs.push(backfilled);
          if (!options.dryRun) {
            await doc.ref.update({
              payload: encoded(MaintenanceLog.encode(backfilled).finish()),
              lastUpdateTimestamp: FieldValue.serverTimestamp(),
            });
          }
        }

        // ── The overview, from the logs as they now stand. ──
        const overviewSnap = await thingRef.collection(KIND_OVERVIEW).get();
        for (const doc of overviewSnap.docs) {
          scannedOverviews++;
          const overview = decode(doc.data() as SyncDocWire, MaintenanceOverview);
          if (overview == null) {
            undecodable.push({ uid, thingId, kind: KIND_OVERVIEW, docId: doc.id });
            continue;
          }
          const rebuilt = backfillOverviewCurrent(overview, logs);
          if (rebuilt == null) continue;
          overviewsRebuilt++;
          if (!options.dryRun) {
            await doc.ref.update({
              payload: encoded(MaintenanceOverview.encode(rebuilt).finish()),
              lastUpdateTimestamp: FieldValue.serverTimestamp(),
            });
          }
        }

        // ── Tasks. ──
        const taskSnap = await thingRef.collection(KIND_TASK).get();
        for (const doc of taskSnap.docs) {
          scannedTasks++;
          const task = decode(doc.data() as SyncDocWire, MaintenanceTask);
          if (task == null) {
            undecodable.push({ uid, thingId, kind: KIND_TASK, docId: doc.id });
            continue;
          }
          const result = backfillTaskMeters(task);
          if (result == null) continue;
          tasksBackfilled++;
          rulesConverted += result.counts.rulesConverted;
          if (result.counts.forcedDueConverted) forcedDuesConverted++;
          if (result.counts.compliedConverted) compliedStatusesConverted++;
          if (!options.dryRun) {
            await doc.ref.update({
              payload: encoded(MaintenanceTask.encode(result.task).finish()),
              lastUpdateTimestamp: FieldValue.serverTimestamp(),
            });
          }
        }
      }
    }
  }

  return {
    dryRun: options.dryRun,
    scannedUids: uids.length,
    scannedThings,
    scannedLogs,
    scannedTasks,
    scannedOverviews,
    logsBackfilled,
    logReadingsWritten,
    overviewsRebuilt,
    tasksBackfilled,
    rulesConverted,
    forcedDuesConverted,
    compliedStatusesConverted,
    undecodable,
    elapsedMs: Date.now() - startedAtMs,
  };
}
