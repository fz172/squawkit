import { createHash } from "node:crypto";

import functionsTest from "firebase-functions-test";

import { adminDb, adminStorage } from "../src/config/firebaseAdmin.js";

/** Shared callable-test harness: one firebase-functions-test instance, one request shape. */
export const fft = functionsTest();

export { adminDb, adminStorage };

const APP_ID = "1:811416892017:android:27fbaf1c76bb16a3f961d0";

export const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");

/** A callable request with App Check and a non-anonymous provider, unless one is given. */
export function req(uid: string, data: unknown, provider = "google.com") {
  return {
    data,
    auth: { uid, token: { firebase: { sign_in_provider: provider } } },
    app: { appId: APP_ID },
  } as never;
}
