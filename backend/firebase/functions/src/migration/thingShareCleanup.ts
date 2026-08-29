import { adminDb } from "../config/firebaseAdmin.js";

/**
 * The ACL tree deletion pass (docs/product/thing_migration_design.md §5.4 step 4, task G6).
 *
 * Deletes `aircraft_shares/{hostUid}/aircraft/{acId}` and its `members/*` / `invites/*`
 * subcollections, once the replica at `thing_shares/{hostUid}/thing/{acId}` has been re-proven
 * complete. The counterpart to `thingShareCutover.ts`, and a separate program for the same reason
 * the entity cleanup is: the cutover's safety property is that it never deletes.
 *
 * **What authorises a delete is re-verification, not elapsed time.** G1/G2 verified the copy when it
 * ran, but that was days ago and shares change — a member joins, a role is updated, an invite is
 * minted. So each share is re-checked here against live data before its old copy goes: the root
 * document present, every `memberRoles` entry matching, and every member and invite document
 * present by id. Anything that fails is skipped and reported, never deleted.
 *
 * The 7-day window (G5) is the separate, human half: it is about every device picking up the G3
 * client, since an un-updated one still reads this tree. Nothing stores a per-share distribution
 * timestamp, so it is asserted with `graceElapsed` rather than derived from a date this could only
 * guess at.
 *
 * Paths are literal here, exactly as in the cutover, and for the same reason: `sharingModels.ts`'s
 * constants now name `thing_shares`, so consuming them would make this delete the tree it is
 * supposed to be preserving.
 */

const LEGACY_SHARES_ROOT = "aircraft_shares";
const LEGACY_SHARES_SUBCOLLECTION = "aircraft";
const THING_SHARES_ROOT = "thing_shares";
const THING_SHARES_SUBCOLLECTION = "thing";
const SUBCOLLECTIONS = ["members", "invites"] as const;

export type ShareCleanupOptions = {
  /** Report what would be deleted; delete nothing. */
  dryRun: boolean;
  /** Restrict to these host uids. Empty/absent means every host. */
  onlyHosts?: readonly string[];
  /**
   * The operator's assertion that G5's 7-day window has elapsed since the G3 client reached every
   * device. Required for a live run — an un-updated client still reads this tree.
   */
  graceElapsed: boolean;
};

export type ShareCleanupOutcome = {
  hostUid: string;
  ok: boolean;
  error?: string;
  sharesDeleted: number;
  sharesSkipped: number;
  documentsDeleted: number;
};

export type ShareCleanupReport = {
  dryRun: boolean;
  startedAtMs: number;
  finishedAtMs: number;
  succeeded: string[];
  failed: { hostUid: string; error: string }[];
  /** Every share left in place, and why. Read this before re-running. */
  skipped: { hostUid: string; acId: string; reason: string }[];
  outcomes: ShareCleanupOutcome[];
  totals: Omit<ShareCleanupOutcome, "hostUid" | "ok" | "error">;
};

function emptyOutcome(hostUid: string): ShareCleanupOutcome {
  return { hostUid, ok: true, sharesDeleted: 0, sharesSkipped: 0, documentsDeleted: 0 };
}

export async function runThingShareCleanup(
  options: ShareCleanupOptions,
): Promise<ShareCleanupReport> {
  if (!options.dryRun && !options.graceElapsed) {
    // A refusal, not a warning. Until every device runs the G3 client, this tree is still being
    // read — deleting it early is what makes a member's roster go empty.
    throw new Error(
      "Refusing to delete: G5's grace window has not been asserted. Pass graceElapsed once 7 days " +
        "have passed since the G3 client reached every device, or use dryRun to preview.",
    );
  }

  const startedAtMs = Date.now();
  const hosts =
    options.onlyHosts != null && options.onlyHosts.length > 0
      ? [...options.onlyHosts]
      : (await adminDb.collection(LEGACY_SHARES_ROOT).listDocuments()).map((ref) => ref.id);

  const outcomes: ShareCleanupOutcome[] = [];
  const skipped: ShareCleanupReport["skipped"] = [];

  for (const hostUid of hosts) {
    const outcome = emptyOutcome(hostUid);
    try {
      await cleanHost(hostUid, options, outcome, skipped);
    } catch (e) {
      outcome.ok = false;
      outcome.error = String(e);
    }
    outcomes.push(outcome);
  }

  const totals = outcomes.reduce(
    (acc, o) => ({
      sharesDeleted: acc.sharesDeleted + o.sharesDeleted,
      sharesSkipped: acc.sharesSkipped + o.sharesSkipped,
      documentsDeleted: acc.documentsDeleted + o.documentsDeleted,
    }),
    { sharesDeleted: 0, sharesSkipped: 0, documentsDeleted: 0 },
  );

  return {
    dryRun: options.dryRun,
    startedAtMs,
    finishedAtMs: Date.now(),
    succeeded: outcomes.filter((o) => o.ok).map((o) => o.hostUid),
    failed: outcomes
      .filter((o) => !o.ok)
      .map((o) => ({ hostUid: o.hostUid, error: o.error ?? "unknown" })),
    skipped,
    outcomes,
    totals,
  };
}

async function cleanHost(
  hostUid: string,
  options: ShareCleanupOptions,
  outcome: ShareCleanupOutcome,
  skipped: ShareCleanupReport["skipped"],
): Promise<void> {
  const shareRefs = await adminDb
    .collection(`${LEGACY_SHARES_ROOT}/${hostUid}/${LEGACY_SHARES_SUBCOLLECTION}`)
    .listDocuments();

  for (const shareRef of shareRefs) {
    const acId = shareRef.id;
    const reason = await verifyReplicated(hostUid, acId);
    if (reason != null) {
      // Left exactly where it is. A share that cannot be proven replicated is one whose only
      // intact ACL may be the copy about to be deleted — and a lost ACL is every member of that
      // aircraft losing access at once.
      outcome.sharesSkipped++;
      skipped.push({ hostUid, acId, reason });
      continue;
    }

    for (const name of SUBCOLLECTIONS) {
      outcome.documentsDeleted += (await shareRef.collection(name).count().get()).data().count;
    }
    const snap = await shareRef.get();
    if (snap.exists) outcome.documentsDeleted++;

    if (!options.dryRun) await adminDb.recursiveDelete(shareRef);
    outcome.sharesDeleted++;
  }

  // The `aircraft_shares/{hostUid}` parent is deliberately left alone. It carries no fields — it
  // exists only as the parent of the subcollection — so it holds nothing and costs nothing, and
  // removing it would mean a second delete against a path a concurrent write could recreate.
}

/**
 * Re-prove the replica for one share. `null` means safe to delete; anything else is the reason it
 * is not.
 *
 * Re-derived from live data rather than trusting G1/G2's report, because that report is days old by
 * the time this runs and shares are not static: a member joins, a role changes, an invite is minted.
 * The question that justifies deleting is "is the replica complete NOW", and only this answers it.
 */
async function verifyReplicated(hostUid: string, acId: string): Promise<string | null> {
  const sourcePath = `${LEGACY_SHARES_ROOT}/${hostUid}/${LEGACY_SHARES_SUBCOLLECTION}/${acId}`;
  const destPath = `${THING_SHARES_ROOT}/${hostUid}/${THING_SHARES_SUBCOLLECTION}/${acId}`;

  const [sourceSnap, destSnap] = await Promise.all([
    adminDb.doc(sourcePath).get(),
    adminDb.doc(destPath).get(),
  ]);

  if (sourceSnap.exists && !destSnap.exists) return "no replica at thing_shares";

  if (sourceSnap.exists) {
    // memberRoles is what security rules authorize against, so a replica missing an entry is a
    // member who loses access the moment the source goes.
    const sourceRoles = (sourceSnap.data()?.memberRoles ?? {}) as Record<string, string>;
    const destRoles = (destSnap.data()?.memberRoles ?? {}) as Record<string, string>;
    for (const [uid, role] of Object.entries(sourceRoles)) {
      if (destRoles[uid] !== role) {
        return `memberRoles mismatch for ${uid} (${role} vs ${destRoles[uid] ?? "absent"})`;
      }
    }
  }

  for (const name of SUBCOLLECTIONS) {
    const sourceIds = (await adminDb.collection(`${sourcePath}/${name}`).get()).docs.map(
      (d) => d.id,
    );
    if (sourceIds.length === 0) continue;
    const destIds = new Set(
      (await adminDb.collection(`${destPath}/${name}`).get()).docs.map((d) => d.id),
    );
    // By id, not by count: equal counts with different ids would pass a count check while having
    // lost a member document — a collaborator who silently vanishes from the roster.
    const missing = sourceIds.filter((id) => !destIds.has(id));
    if (missing.length > 0) {
      return `${missing.length} of ${sourceIds.length} ${name} missing at destination (e.g. ${missing[0]})`;
    }
  }

  return null;
}
