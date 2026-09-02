import { adminDb } from "../config/firebaseAdmin.js";

/**
 * The ACL tree cutover (docs/product/thing_migration_design.md §5.4, tasks G1–G2).
 *
 * Copies `aircraft_shares/{hostUid}/aircraft/{acId}` — plus its `members/*` and `invites/*`
 * subcollections — to `thing_shares/{hostUid}/thing/{acId}`. Copy only; deleting the old tree is
 * G6, after its own grace window.
 *
 * **These paths are written out literally rather than built from `sharingModels.ts`'s constants,
 * and that is deliberate.** Task B6 flips those very constants, and a migration that consumed them
 * would silently start reading and writing the *same* tree the moment it did — copying a tree onto
 * itself and reporting success. A migration between two names cannot depend on the constant that
 * names them. Same reasoning as `entitySegment.ts` holding both segments explicitly.
 *
 * Why this is separate from the entity cutover even though the shape is nearly identical: the two
 * trees move at different times (§2.9). The entity tree moved in Phase D; the ACL tree cannot move
 * until a client release that reads the new location has shipped, which is a second release and a
 * second grace window. Folding them together would have coupled those schedules for no benefit.
 */

/** The tree being migrated away from. Literal on purpose — see the note above. */
const LEGACY_SHARES_ROOT = "aircraft_shares";
const LEGACY_SHARES_SUBCOLLECTION = "aircraft";

/** The tree being migrated to. */
const THING_SHARES_ROOT = "thing_shares";
const THING_SHARES_SUBCOLLECTION = "thing";

/** Subcollection names, unchanged by this migration — they were never named after the aircraft. */
const SUBCOLLECTIONS = ["members", "invites"] as const;

export type ShareCutoverOptions = {
  /** Read and count; write nothing. */
  dryRun: boolean;
  /** Restrict to these host uids. Empty/absent means every host with a share. */
  onlyHosts?: readonly string[];
};

export type ShareHostOutcome = {
  hostUid: string;
  ok: boolean;
  error?: string;
  sharesCopied: number;
  membersCopied: number;
  invitesCopied: number;
};

export type ShareCutoverReport = {
  dryRun: boolean;
  startedAtMs: number;
  finishedAtMs: number;
  succeeded: string[];
  failed: { hostUid: string; error: string }[];
  /** Shares whose copy did not verify. Non-empty means do NOT proceed to G3. */
  mismatched: { hostUid: string; acId: string; reason: string }[];
  outcomes: ShareHostOutcome[];
  totals: Omit<ShareHostOutcome, "hostUid" | "ok" | "error">;
};

function emptyOutcome(hostUid: string): ShareHostOutcome {
  return { hostUid, ok: true, sharesCopied: 0, membersCopied: 0, invitesCopied: 0 };
}

/**
 * Every host that owns at least one share.
 *
 * `listDocuments()` rather than `get()`: an `aircraft_shares/{hostUid}` document has no fields of
 * its own — it exists only as the parent of the `aircraft` subcollection — so `get()` returns
 * nothing and a `.get()`-based sweep would find zero hosts on a tree full of live shares.
 */
async function allHosts(): Promise<string[]> {
  const refs = await adminDb.collection(LEGACY_SHARES_ROOT).listDocuments();
  return refs.map((ref) => ref.id);
}

export async function runThingShareCutover(
  options: ShareCutoverOptions,
): Promise<ShareCutoverReport> {
  const startedAtMs = Date.now();
  const hosts =
    options.onlyHosts != null && options.onlyHosts.length > 0
      ? [...options.onlyHosts]
      : await allHosts();

  const outcomes: ShareHostOutcome[] = [];
  const mismatched: ShareCutoverReport["mismatched"] = [];

  for (const hostUid of hosts) {
    const outcome = emptyOutcome(hostUid);
    try {
      await copyHost(hostUid, options, outcome, mismatched);
    } catch (e) {
      outcome.ok = false;
      outcome.error = String(e);
    }
    outcomes.push(outcome);
  }

  const totals = outcomes.reduce(
    (acc, o) => ({
      sharesCopied: acc.sharesCopied + o.sharesCopied,
      membersCopied: acc.membersCopied + o.membersCopied,
      invitesCopied: acc.invitesCopied + o.invitesCopied,
    }),
    { sharesCopied: 0, membersCopied: 0, invitesCopied: 0 },
  );

  return {
    dryRun: options.dryRun,
    startedAtMs,
    finishedAtMs: Date.now(),
    succeeded: outcomes.filter((o) => o.ok).map((o) => o.hostUid),
    failed: outcomes
      .filter((o) => !o.ok)
      .map((o) => ({ hostUid: o.hostUid, error: o.error ?? "unknown" })),
    mismatched,
    outcomes,
    totals,
  };
}

async function copyHost(
  hostUid: string,
  options: ShareCutoverOptions,
  outcome: ShareHostOutcome,
  mismatched: ShareCutoverReport["mismatched"],
): Promise<void> {
  const shareRefs = await adminDb
    .collection(`${LEGACY_SHARES_ROOT}/${hostUid}/${LEGACY_SHARES_SUBCOLLECTION}`)
    .listDocuments();

  for (const shareRef of shareRefs) {
    const acId = shareRef.id;
    const destPath = `${THING_SHARES_ROOT}/${hostUid}/${THING_SHARES_SUBCOLLECTION}/${acId}`;

    const snap = await shareRef.get();
    if (snap.exists) {
      outcome.sharesCopied++;
      // Copied verbatim. `hostUid` and `thingId` keep their field names and values — this
      // migration moves the ACL's LOCATION, not its schema, and renaming fields here would break
      // `shareRole()` and every function that reads the doc for no gain (§3.3).
      if (!options.dryRun) await adminDb.doc(destPath).set(snap.data() as Record<string, unknown>);
    }

    for (const name of SUBCOLLECTIONS) {
      const docs = await shareRef.collection(name).get();
      for (const doc of docs.docs) {
        if (name === "members") outcome.membersCopied++;
        else outcome.invitesCopied++;
        if (!options.dryRun) {
          await adminDb.doc(`${destPath}/${name}/${doc.id}`).set(doc.data());
        }
      }
    }

    if (!options.dryRun) {
      const reason = await verifyShare(hostUid, acId);
      if (reason != null) mismatched.push({ hostUid, acId, reason });
    }
  }
}

/**
 * G2: confirm the copy landed, per share. Returns `null` when it matches, or the reason it does not.
 *
 * Compared by document id, not by count, for the same reason the entity cleanup does: equal counts
 * with different ids would pass a count check while having lost a member — and a lost member doc is
 * a collaborator who silently disappears from the roster.
 *
 * `memberRoles` is compared explicitly too. It is the field security rules actually authorize
 * against, so a share whose subcollections copied but whose root roles did not is worse than one
 * that failed outright: it looks migrated and denies everyone.
 */
async function verifyShare(hostUid: string, acId: string): Promise<string | null> {
  const sourcePath = `${LEGACY_SHARES_ROOT}/${hostUid}/${LEGACY_SHARES_SUBCOLLECTION}/${acId}`;
  const destPath = `${THING_SHARES_ROOT}/${hostUid}/${THING_SHARES_SUBCOLLECTION}/${acId}`;

  const [sourceSnap, destSnap] = await Promise.all([
    adminDb.doc(sourcePath).get(),
    adminDb.doc(destPath).get(),
  ]);

  if (sourceSnap.exists && !destSnap.exists) return "share doc missing at destination";

  if (sourceSnap.exists) {
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
    const missing = sourceIds.filter((id) => !destIds.has(id));
    if (missing.length > 0) {
      return `${missing.length} of ${sourceIds.length} ${name} missing (e.g. ${missing[0]})`;
    }
  }

  return null;
}
