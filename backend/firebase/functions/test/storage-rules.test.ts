import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const rulesPath = resolve(dirname(fileURLToPath(import.meta.url)), "../../storage.rules");

const HOST = "host-uid";
const MEMBER = "member-uid";
const AC = "ac-1";

// Blobs are written at `{entityScope}/blobs/{blobId}`, so an attachment on the host's aircraft lands
// under the host's tree.
const hostBlob = `users/${HOST}/aircraft/${AC}/blobs/blob-1`;
const memberBlob = `users/${MEMBER}/aircraft/${AC}/blobs/blob-1`;

let testEnv: RulesTestEnvironment;

// initializeTestEnvironment compiles storage.rules and throws on a malformed file — so this suite is
// also the syntax gate. (Booting the emulator alone is NOT: it merely warns and exits 0.)
beforeAll(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-squawkit",
    storage: { rules: readFileSync(rulesPath, "utf8") },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearStorage();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await uploadBytes(ref(ctx.storage(), hostBlob), new Uint8Array([1, 2, 3]));
  });
});

const as = (uid: string) => testEnv.authenticatedContext(uid).storage();

describe("storage rules — blobs are strictly uid-scoped", () => {
  it("the owner reads and writes their own blobs", async () => {
    await assertSucceeds(getBytes(ref(as(HOST), hostBlob)));
    await assertSucceeds(uploadBytes(ref(as(HOST), hostBlob), new Uint8Array([4])));
  });

  it("a member of the shared aircraft may NOT read the host's blobs", async () => {
    // This is design §9, and it is deliberate: sharing an aircraft does NOT share its attachment
    // blobs. Storage rules cannot get() Firestore, so they cannot consult the aircraft_shares ACL —
    // crossing the boundary needs a signed-URL broker ([P8] #196). Until then the client degrades
    // honestly (#146) rather than handing the user a tap that fails.
    //
    // If this test ever starts failing because someone widened the match, read #196 first: the
    // "obvious" fix hands every member read access to the host's ENTIRE storage tree.
    await assertFails(getBytes(ref(as(MEMBER), hostBlob)));
  });

  it("a member may NOT upload into the host's tree", async () => {
    // Why the attach affordance is hidden on a shared aircraft: the upload would be denied here, and
    // the file would sit on the device forever.
    await assertFails(uploadBytes(ref(as(MEMBER), hostBlob), new Uint8Array([9])));
  });

  it("a member reads and writes their OWN tree normally", async () => {
    await assertSucceeds(uploadBytes(ref(as(MEMBER), memberBlob), new Uint8Array([7])));
    await assertSucceeds(getBytes(ref(as(MEMBER), memberBlob)));
  });

  it("an unauthenticated caller gets nothing", async () => {
    const anon = testEnv.unauthenticatedContext().storage();
    await assertFails(getBytes(ref(anon, hostBlob)));
    await assertFails(uploadBytes(ref(anon, hostBlob), new Uint8Array([9])));
  });
});
