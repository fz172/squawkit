import { createHash } from "node:crypto";

import { adminStorage } from "../../config/firebaseAdmin.js";
import { blobObjectPath } from "../../storage/blobBroker.js";
import { AiError } from "../errors.js";
import type { SourceDocumentRef } from "./model.js";

/** Where a job's documents live: the Thing's tree, which is the host's. */
export type DocumentOwner = { hostUid: string; thingId: string };

/**
 * A run's document, read from Storage for the pipeline (design §5.5, §8.1). The bytes are the
 * Thing's own blob, under the host's tree, uploaded by the app before it started the run.
 *
 * Refused, before anything is downloaded where it can be:
 * - `document_missing` when the blob is not there, or its bytes are not the ones the app named
 *   (`sha256`): a stale reference, or an upload that never finished;
 * - `document_too_large` when it is over [maxBytes] (`ai_config.maxDocumentBytes`).
 *
 * The path is built here from the job and the blob id, never taken from the request, so a request
 * cannot point the worker at another Thing's files.
 */
export async function loadJobDocument(
  owner: DocumentOwner,
  ref: SourceDocumentRef,
  maxBytes: number,
): Promise<Uint8Array> {
  if (!ref.blobId || ref.blobId.includes("/")) {
    throw new AiError("document_missing", "the document names no usable blob");
  }
  const file = adminStorage.bucket().file(blobObjectPath(owner.hostUid, owner.thingId, ref.blobId));

  let size: number;
  try {
    const [metadata] = await file.getMetadata();
    size = Number(metadata.size);
  } catch {
    throw new AiError("document_missing", `${ref.blobId} is not in Storage`);
  }
  if (size > maxBytes) {
    throw new AiError("document_too_large", `${ref.blobId} is ${size} bytes, over ${maxBytes}`);
  }

  const [bytes] = await file.download();
  if (ref.sha256 && createHash("sha256").update(bytes).digest("hex") !== ref.sha256.toLowerCase()) {
    throw new AiError("document_missing", `${ref.blobId} does not match its sha256`);
  }
  return new Uint8Array(bytes);
}
