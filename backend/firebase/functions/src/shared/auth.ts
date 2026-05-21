import { HttpsError, type CallableRequest } from "firebase-functions/v2/https";

const ALLOWED_APP_IDS = new Set([
  "1:811416892017:android:27fbaf1c76bb16a3f961d0",
  "1:811416892017:ios:e04bbe689405347df961d0",
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
