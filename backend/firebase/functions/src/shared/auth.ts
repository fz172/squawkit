import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";

const ALLOWED_APP_IDS = new Set([
  "1:811416892017:android:27fbaf1c76bb16a3f961d0",
  "1:811416892017:ios:e04bbe689405347df961d0",
  "1:811416892017:web:6680df6dd37a69d1f961d0",
]);

export type CallableIdentity = {
  uid: string;
  appId: string;
};

export function requireAuthenticatedApp(request: CallableRequest<unknown>): CallableIdentity {
  if (request.auth == null) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  const appId = request.app?.appId;
  if (appId == null || !ALLOWED_APP_IDS.has(appId)) {
    throw new HttpsError("permission-denied", "Unauthorized app.");
  }

  return {
    uid: request.auth.uid,
    appId,
  };
}

/**
 * As [requireAuthenticatedApp], but also requires the caller's token to carry an `admin` custom
 * claim. Gates operations no ordinary user may perform — e.g. writing a comp'd (SERVER_GRANT)
 * entitlement. The claim is set out of band via the Admin SDK; a normal sign-in never has it, so a
 * forged request without it is rejected here rather than reaching the write.
 */
export function requireAdmin(request: CallableRequest<unknown>): CallableIdentity {
  const identity = requireAuthenticatedApp(request);
  if (request.auth?.token?.admin !== true) {
    throw new HttpsError("permission-denied", "Admin only.");
  }
  return identity;
}
