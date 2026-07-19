import { Writable } from "node:stream";

import functionsTest from "firebase-functions-test";
import { afterAll, afterEach, beforeEach, describe, expect, it } from "vitest";

import { adminDb, adminStorage, req } from "./helpers.js";

import { authorizeBlobRead, blobObjectPath } from "../src/storage/blobBroker.js";
import { getBlobUploadSession } from "../src/storage/getBlobUploadSession.js";
import { pipeBlob, type BlobResponse } from "../src/storage/streamBlob.js";

const fft = functionsTest();
const wrappedUploadSession = fft.wrap(getBlobUploadSession);

const HOST = "host-uid";
const MEMBER = "member-uid";
const STRANGER = "stranger-uid";
const AC = "ac-1";
const BLOB = "blob-abc";

async function seedShare(
  memberRoles: Record<string, string> = { [HOST]: "owner", [MEMBER]: "technician" },
  extra: Record<string, unknown> = {},
) {
  await adminDb
    .doc(`aircraft_shares/${HOST}/aircraft/${AC}`)
    .set({ hostUid: HOST, aircraftId: AC, memberRoles, ...extra });
}

async function wipe() {
  await adminDb.recursiveDelete(adminDb.doc(`aircraft_shares/${HOST}/aircraft/${AC}`));
  await adminStorage.bucket().deleteFiles({ prefix: `users/${HOST}/` });
}

beforeEach(wipe);
afterEach(wipe);
afterAll(() => fft.cleanup());

describe("authorizeBlobRead", () => {
  it("allows a member and the host, denies a stranger", async () => {
    await seedShare();
    expect(await authorizeBlobRead(MEMBER, HOST, AC)).toBe(true);
    expect(await authorizeBlobRead(HOST, HOST, AC)).toBe(true);
    expect(await authorizeBlobRead(STRANGER, HOST, AC)).toBe(false);
  });

  it("denies a member the instant they are revoked (re-checked per call)", async () => {
    await seedShare();
    expect(await authorizeBlobRead(MEMBER, HOST, AC)).toBe(true);

    // Simulate revoke: drop MEMBER from the ACL. No minted URL to outlive it.
    await seedShare({ [HOST]: "owner" });
    expect(await authorizeBlobRead(MEMBER, HOST, AC)).toBe(false);
  });

  it("denies when no share exists", async () => {
    expect(await authorizeBlobRead(MEMBER, HOST, AC)).toBe(false);
  });
});

describe("pipeBlob", () => {
  it("streams the object's bytes and content-type to a member", async () => {
    await adminStorage
      .bucket()
      .file(blobObjectPath(HOST, AC, BLOB))
      .save(Buffer.from("hello-bytes"), { contentType: "image/png", resumable: false });

    const res = mockRes();
    await pipeBlob(HOST, AC, BLOB, res.sink);

    expect(res.status()).toBe(200);
    expect(res.header("Content-Type")).toBe("image/png");
    expect(res.header("Cache-Control")).toBe("private, no-store");
    // Cross-account content must never be sniffed or rendered inline in this origin.
    expect(res.header("X-Content-Type-Options")).toBe("nosniff");
    expect(res.header("Content-Disposition")).toBe("attachment");
    expect(res.body().toString()).toBe("hello-bytes");
  });

  it("404s a missing blob without streaming a body", async () => {
    const res = mockRes();
    await pipeBlob(HOST, AC, "does-not-exist", res.sink);

    expect(res.status()).toBe(404);
    expect(res.body().length).toBe(0);
  });
});

describe("getBlobUploadSession", () => {
  it("mints a resumable upload session for a member", async () => {
    await seedShare();
    const out = await wrappedUploadSession(
      req(MEMBER, { hostUid: HOST, aircraftId: AC, blobId: BLOB, contentType: "image/jpeg" }),
    );
    expect(typeof out.uploadUrl).toBe("string");
    expect(out.uploadUrl.length).toBeGreaterThan(0);
  });

  it("rejects a non-member", async () => {
    await seedShare({ [HOST]: "owner" });
    await expect(
      wrappedUploadSession(req(STRANGER, { hostUid: HOST, aircraftId: AC, blobId: BLOB })),
    ).rejects.toThrow();
  });

  it("rejects when attachments are explicitly disabled for the aircraft", async () => {
    await seedShare(undefined, { attachmentsEnabled: false });
    await expect(
      wrappedUploadSession(req(MEMBER, { hostUid: HOST, aircraftId: AC, blobId: BLOB })),
    ).rejects.toThrow();
  });

  it("allows when attachmentsEnabled is absent (projector not built yet — stub true)", async () => {
    await seedShare(); // no attachmentsEnabled field
    const out = await wrappedUploadSession(
      req(MEMBER, { hostUid: HOST, aircraftId: AC, blobId: BLOB }),
    );
    expect(typeof out.uploadUrl).toBe("string");
  });
});

/** A minimal Express-response stand-in: a Writable that also records headers and status. */
function mockRes() {
  const chunks: Buffer[] = [];
  const headers: Record<string, string> = {};
  // Unset until pipeBlob assigns one, so a status assertion actually proves pipeBlob set it rather
  // than reading a convenient default.
  let statusCode: number | undefined;
  const sink = new Writable({
    write(chunk, _enc, cb) {
      chunks.push(Buffer.from(chunk));
      cb();
    },
  }) as unknown as BlobResponse & { setHeader: (k: string, v: string | number) => void };
  sink.setHeader = (k, v) => {
    headers[k] = String(v);
  };
  sink.status = (code) => {
    statusCode = code;
    return sink;
  };
  return {
    sink,
    status: () => statusCode,
    header: (k: string) => headers[k],
    body: () => Buffer.concat(chunks),
  };
}
