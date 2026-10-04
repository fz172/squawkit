import { createHash, randomUUID } from "node:crypto";

import { describe, expect, it } from "vitest";

import { AiError } from "../../src/ai/errors.js";
import { loadJobDocument } from "../../src/ai/tasks/documentLoader.js";
import type { SourceDocumentRef } from "../../src/ai/tasks/model.js";
import { adminStorage } from "../../src/config/firebaseAdmin.js";
import { blobObjectPath } from "../../src/storage/blobBroker.js";

// T21: a run's document, from the Thing's blobs, on the Storage emulator.

const PDF = Buffer.from("%PDF-1.4 a small manual");
const sha = (bytes: Buffer) => createHash("sha256").update(bytes).digest("hex");

async function stored(bytes: Buffer = PDF) {
  const owner = { hostUid: `h-${randomUUID()}`, thingId: `t-${randomUUID()}` };
  const blobId = `b-${randomUUID()}`;
  await adminStorage.bucket().file(blobObjectPath(owner.hostUid, owner.thingId, blobId)).save(bytes);
  const ref: SourceDocumentRef = { blobId, name: "manual.pdf", mimeType: "application/pdf", sha256: sha(bytes), sizeBytes: bytes.length };
  return { owner, ref };
}

async function codeOf(p: Promise<unknown>): Promise<string> {
  const e = await p.then(() => null, (err: unknown) => err);
  expect(e).toBeInstanceOf(AiError);
  return (e as AiError).code;
}

describe("loadJobDocument", () => {
  it("reads the Thing's blob, checked against its sha256", async () => {
    const { owner, ref } = await stored();

    expect(Buffer.from(await loadJobDocument(owner, ref, 1024))).toEqual(PDF);
  });

  it("refuses one over the cap before downloading it", async () => {
    const { owner, ref } = await stored(Buffer.alloc(2048, 1));

    expect(await codeOf(loadJobDocument(owner, ref, 1024))).toBe("document_too_large");
  });

  it("says a blob that is not there is missing", async () => {
    const { owner, ref } = await stored();

    expect(await codeOf(loadJobDocument(owner, { ...ref, blobId: "never-uploaded" }, 1024))).toBe("document_missing");
  });

  it("says bytes that are not the ones named are missing", async () => {
    const { owner, ref } = await stored();

    expect(await codeOf(loadJobDocument(owner, { ...ref, sha256: sha(Buffer.from("other")) }, 1024))).toBe("document_missing");
  });

  it("only looks in the job's own Thing", async () => {
    const { owner, ref } = await stored();

    // Same blob id, another Thing: not found, rather than read across trees.
    expect(await codeOf(loadJobDocument({ ...owner, thingId: "another" }, ref, 1024))).toBe("document_missing");
    expect(await codeOf(loadJobDocument(owner, { ...ref, blobId: "../t/blobs/x" }, 1024))).toBe("document_missing");
  });
});
