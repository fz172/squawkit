import { createHash, randomInt } from "node:crypto";

import type { Timestamp } from "firebase-admin/firestore";

import type { ShareRole } from "./sharingModels.js";

/**
 * Pairing-code invites (#164).
 *
 * The code lives in its OWN top-level collection, keyed by the code itself, and **no client may read
 * or write it** — only the callables, as admin. That is the security property, and it is what the
 * old `/share#{hostUid}.{acId}.{secret}` link could not give us: the invitee never holds an aircraft
 * id or a host uid, so there is nothing to fabricate a same-id aircraft against (#202/#204). The code
 * names nothing real and is derived from nothing real; it is an opaque bearer handle that only the
 * server can dereference.
 *
 * Single-use is total: redeem DELETES the doc. "Already used" and "never existed" collapse into the
 * same state, which is exactly the one error the PRD asks for (C3/C4/C5 all say the same thing), so
 * there is no useCount/revoked bookkeeping to get wrong.
 */
export const INVITE_CODES_COLLECTION = "invite_codes";

export function inviteCodeDocPath(code: string): string {
  return `${INVITE_CODES_COLLECTION}/${code}`;
}

/**
 * The invite. `code` is the doc id — the whole document is the secret, which is why clients are
 * denied it outright rather than being given a hash to read.
 */
export type InviteCodeDoc = {
  hostUid: string;
  aircraftId: string;
  role: ShareRole;
  createdBy: string;
  createdAt: Timestamp;
  expiresAt: Timestamp;
  /**
   * What the invitee is shown before accepting (#201) — e.g. "N2037O · Cessna 172", and the host's
   * name. Denormalized HERE because the aircraft doc is opaque proto bytes in `payload`: the server
   * cannot decode it, so it cannot read the registration out of the record itself. The owner's client
   * supplies the label (it is their own aircraft) and the host name comes from the caller's token.
   */
  aircraftLabel: string;
  hostName: string;
  /**
   * SHA-256 of the code — the same id the owner sees in their pending list, denormalized here so a
   * cancel can find this doc with ONE equality filter. Without it, cancel had to filter on
   * (hostUid, aircraftId) and hash-match every candidate: a compound query needing a composite index
   * that the emulator does not enforce, so it passed in tests and would have failed in production.
   */
  codeId: string;
};

/**
 * Unambiguous when read aloud in a hangar or typed on a phone: no `0/O`, `1/I/L`, or `U` (which is
 * heard as "you"). 30 symbols → 30^8 ≈ 6.5e11, about 39 bits.
 *
 * That is far less than the 128-bit link secret it replaces, and it is only safe because of the
 * guardrails around it: a **1-day expiry**, **single use** (the doc is deleted), and **rate-limited**
 * dereferencing (see rateLimit.ts). Weaken any of those three and 39 bits becomes brute-forceable.
 */
const ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
const CODE_LENGTH = 8;

/** How long a code stays live. Short on purpose: it is the main thing standing in for entropy. */
export const INVITE_TTL_MS = 24 * 60 * 60 * 1000;

/** CSPRNG. `randomInt` is rejection-sampled, so no modulo bias across the 30-symbol alphabet. */
export function generateInviteCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += ALPHABET[randomInt(ALPHABET.length)];
  }
  return code;
}

/**
 * Accept what a human actually types: lowercase, spaces, and the `EFA1-GGTH` grouping we display in.
 * Anything outside the alphabet is dropped rather than rejected, so a stray dash or space is not an
 * error message. Returns "" if nothing valid survives.
 */
export function normalizeInviteCode(input: string): string {
  // Separators are stripped; anything else outside the alphabet is a REJECTION, not a deletion.
  // Filtering unknown characters away silently turns junk into a well-formed code — a legacy
  // `ac-1.sEcReT` link reduced to `ACSECRET`. Must match InviteCode.kt on the client.
  const cleaned = input
    .toUpperCase()
    .split("")
    .filter((ch) => ch !== "-" && !/\s/.test(ch))
    .join("");
  if (cleaned.length !== CODE_LENGTH) return "";
  return cleaned.split("").every((ch) => ALPHABET.includes(ch)) ? cleaned : "";
}

/** `EFA1GGTH` → `EFA1-GGTH`. Display only; the stored id is unformatted. */
export function formatInviteCode(code: string): string {
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

/**
 * Owner-visible record of a pending invite, at `.../invites/{codeId}` where `codeId = SHA-256(code)`.
 *
 * The code itself is NOT stored here. The owner can see that an invite exists, for what role, and
 * when it expires — enough to manage it — but a read of this collection yields nothing redeemable.
 */
export function inviteCodeId(code: string): string {
  return createHash("sha256").update(code).digest("hex");
}
