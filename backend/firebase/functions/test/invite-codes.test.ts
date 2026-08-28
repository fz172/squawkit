import { beforeEach, describe, expect, it } from "vitest";

import { adminDb, fft, req, sha256 } from "./helpers.js";

import { cancelAircraftShareInvite } from "../src/sharing/cancelAircraftShareInvite.js";
import { createAircraftShareInvite } from "../src/sharing/createAircraftShareInvite.js";
import { previewAircraftShareInvite } from "../src/sharing/previewAircraftShareInvite.js";
import { redeemAircraftShareInvite } from "../src/sharing/redeemAircraftShareInvite.js";

const wrappedCreate = fft.wrap(createAircraftShareInvite);
const wrappedPreview = fft.wrap(previewAircraftShareInvite);
const wrappedRedeem = fft.wrap(redeemAircraftShareInvite);
const wrappedCancel = fft.wrap(cancelAircraftShareInvite);

const HOST = "host-uid";
const TECH = "tech-uid";
const AC = "ac-code-1";

async function seedAircraft() {
  await adminDb.doc(`users/${HOST}/aircraft/${AC}`).set({ deleted: false, writerUid: HOST });
}

async function mintCode(role = "technician"): Promise<string> {
  const res = (await wrappedCreate(
    req(HOST, { aircraftId: AC, role, aircraftLabel: "N2037O · Cessna 172" }),
  )) as { code: string };
  return res.code;
}

beforeEach(async () => {
  await adminDb.recursiveDelete(adminDb.doc(`aircraft_shares/${HOST}`));
  await adminDb.recursiveDelete(adminDb.collection("invite_codes"));
  await adminDb.recursiveDelete(adminDb.collection("invite_attempts"));
  await adminDb.recursiveDelete(adminDb.doc(`users/${TECH}`));
  await seedAircraft();
});

describe("createAircraftShareInvite", () => {
  it("mints a code, bootstraps the ACL, and never stores the code where a client can read it", async () => {
    const res = (await wrappedCreate(
      req(HOST, { aircraftId: AC, role: "technician", aircraftLabel: "N2037O · Cessna 172" }),
    )) as { code: string; formattedCode: string; codeId: string };

    expect(res.code).toMatch(/^[A-Z2-9]{8}$/);
    expect(res.formattedCode).toBe(`${res.code.slice(0, 4)}-${res.code.slice(4)}`);

    // The ACL is bootstrapped under the HOST, with the host as owner.
    const share = (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).data();
    expect(share?.memberRoles).toEqual({ [HOST]: "owner" });

    // The owner-visible record carries role + expiry, and NOT the code.
    const pending = (
      await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/invites/${res.codeId}`).get()
    ).data();
    expect(pending?.role).toBe("technician");
    expect(JSON.stringify(pending)).not.toContain(res.code);
  });

  it("refuses a non-owner", async () => {
    await mintCode();
    await expect(
      wrappedCreate(req(TECH, { aircraftId: AC, role: "technician", aircraftLabel: "x" })),
    ).rejects.toThrow();
  });
});

describe("redeemAircraftShareInvite", () => {
  it("joins by code alone — no aircraft id, no host uid from the caller", async () => {
    const code = await mintCode();

    const res = (await wrappedRedeem(req(TECH, { code }))) as Record<string, unknown>;

    expect(res).toMatchObject({ aircraftId: AC, hostUid: HOST, role: "technician", alreadyMember: false });
    const share = (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).data();
    expect(share?.memberRoles[TECH]).toBe("technician");
    const ref = await adminDb.doc(`users/${TECH}/shared_aircraft_ref/${AC}`).get();
    expect(ref.exists).toBe(true);
  });

  it("burns the code: single use is total, so a second redeem finds nothing", async () => {
    const code = await mintCode();
    await wrappedRedeem(req(TECH, { code }));

    expect((await adminDb.doc(`invite_codes/${code}`).get()).exists).toBe(false);
    await expect(wrappedRedeem(req("someone-else", { code }))).rejects.toThrow();
  });

  it("accepts what a human types: lowercase, dashes, spaces", async () => {
    const code = await mintCode();
    const typed = `${code.slice(0, 4).toLowerCase()}-${code.slice(4).toLowerCase()}`;

    await expect(wrappedRedeem(req(TECH, { code: typed }))).resolves.toMatchObject({
      alreadyMember: false,
    });
  });

  it("rejects an expired code", async () => {
    const code = await mintCode();
    await adminDb.doc(`invite_codes/${code}`).update({ expiresAt: new Date(Date.now() - 1000) });

    await expect(wrappedRedeem(req(TECH, { code }))).rejects.toThrow();
  });

  it("is a friendly no-op for an existing member, and does NOT burn the code", async () => {
    const code = await mintCode();
    await wrappedRedeem(req(TECH, { code }));
    const code2 = await mintCode();

    const res = (await wrappedRedeem(req(TECH, { code: code2 }))) as Record<string, unknown>;

    expect(res).toMatchObject({ alreadyMember: true });
    // The owner may still be waiting for the person they actually meant to invite.
    expect((await adminDb.doc(`invite_codes/${code2}`).get()).exists).toBe(true);
  });

  it("refuses an anonymous caller", async () => {
    const code = await mintCode();
    await expect(
      wrappedRedeem(req(TECH, { code }, "anonymous")),
    ).rejects.toThrow();
  });
});

describe("previewAircraftShareInvite (#201)", () => {
  it("shows what you are joining, and leaks no ids", async () => {
    const code = await mintCode("owner");

    const res = (await wrappedPreview(req(TECH, { code }))) as Record<string, unknown>;

    expect(res).toEqual({ aircraftLabel: "N2037O · Cessna 172", hostName: "", role: "owner" });
    // The whole point: an invitee must not come away holding an aircraft id or a host uid — those
    // are what #202/#204 turned into a weapon.
    expect(JSON.stringify(res)).not.toContain(AC);
    expect(JSON.stringify(res)).not.toContain(HOST);
  });

  it("refuses an anonymous caller — otherwise the rate limit is free to bypass", async () => {
    // The limiter meters by uid, and anonymous uids are free and unlimited: mint one, spend its 10
    // guesses, mint another. A ~39-bit code only survives because guessing is metered, so a
    // disposable identity defeats the entire design. Redeem always refused anonymous callers;
    // preview must too, because it dereferences the same code.
    const code = await mintCode();

    await expect(wrappedPreview(req("anon-uid", { code }, "anonymous"))).rejects.toThrow();
  });

  it("grants nothing — previewing does not make you a member", async () => {
    const code = await mintCode();

    await wrappedPreview(req(TECH, { code }));

    const share = (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`).get()).data();
    expect(share?.memberRoles[TECH]).toBeUndefined();
    expect((await adminDb.doc(`invite_codes/${code}`).get()).exists).toBe(true); // not burned
  });
});

// A ~39-bit code is only safe because guessing is metered. Preview is the oracle you forget: it
// answers "is this code real?" exactly as well as redeem does, and it feels read-only.
describe("rate limiting — the brute-force budget", () => {
  it("locks out after repeated wrong codes, on redeem", async () => {
    for (let i = 0; i < 10; i++) {
      await expect(wrappedRedeem(req(TECH, { code: "AAAAAAAA" }))).rejects.toThrow();
    }
    // Even a VALID code is now refused — the budget is spent.
    const code = await mintCode();
    await expect(wrappedRedeem(req(TECH, { code }))).rejects.toThrow(/Too many/);
  });

  it("preview burns the SAME budget — it is not a free oracle", async () => {
    for (let i = 0; i < 10; i++) {
      await expect(wrappedPreview(req(TECH, { code: "AAAAAAAA" }))).rejects.toThrow();
    }
    const code = await mintCode();
    await expect(wrappedPreview(req(TECH, { code }))).rejects.toThrow(/Too many/);
    // …and the spend carries across to redeem, so an attacker cannot launder guesses through it.
    await expect(wrappedRedeem(req(TECH, { code }))).rejects.toThrow(/Too many/);
  });

  it("success does not consume budget", async () => {
    const code = await mintCode();
    await wrappedRedeem(req(TECH, { code }));

    const attempts = await adminDb.doc(`invite_attempts/${TECH}`).get();
    expect(attempts.exists).toBe(false);
  });
});

describe("cancelAircraftShareInvite", () => {
  it("finds the code by a single equality filter (no composite index needed)", async () => {
    // The first cut filtered on (hostUid, aircraftId) and hash-matched the results — a compound
    // query needing a composite index. The emulator does not enforce indexes, so it passed here and
    // would have failed in production with FAILED_PRECONDITION. The code doc now carries codeId.
    const code = await mintCode();
    const stored = (await adminDb.doc(`invite_codes/${code}`).get()).data();

    expect(stored?.codeId).toBe(sha256(code));
  });


  it("destroys the code, so a cancelled invite and a never-existed one are the same", async () => {
    const code = await mintCode();
    const codeId = sha256(code);

    await wrappedCancel(req(HOST, { aircraftId: AC, codeId }));

    expect((await adminDb.doc(`invite_codes/${code}`).get()).exists).toBe(false);
    expect(
      (await adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}/invites/${codeId}`).get()).exists,
    ).toBe(false);
    await expect(wrappedRedeem(req(TECH, { code }))).rejects.toThrow();
  });

  it("refuses a non-owner", async () => {
    const code = await mintCode();
    await expect(
      wrappedCancel(req(TECH, { aircraftId: AC, codeId: sha256(code) })),
    ).rejects.toThrow();
  });
});
