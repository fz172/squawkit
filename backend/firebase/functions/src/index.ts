import { HttpsError, onCall } from "firebase-functions/v2/https";

type HealthProbeResponse = {
  status: "ok";
  message: string;
  uid: string;
  appId: string;
};

const ALLOWED_APP_IDS = new Set([
  "1:811416892017:android:27fbaf1c76bb16a3f961d0",
  "1:811416892017:ios:e04bbe689405347df961d0",
]);

export const health_probe = onCall<unknown, HealthProbeResponse>(
  {
    enforceAppCheck: true,
  },
  (request) => {
    if (request.auth == null) {
      throw new HttpsError("unauthenticated", "Sign-in required.")
    }

    const appId = request.app?.appId
    if (appId == null || !ALLOWED_APP_IDS.has(appId)) {
      throw new HttpsError("permission-denied", "Unauthorized app.")
    }

    return {
      status: "ok",
      message: "health probe passed",
      uid: request.auth.uid,
      appId,
    }
  },
)
