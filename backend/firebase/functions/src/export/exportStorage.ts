import { HttpsError } from "firebase-functions/v2/https";

import { adminStorage } from "../config/firebaseAdmin.js";

export class ExportStorage {
  async createSignedDownloadUrl(
    remoteArchiveRef: string,
    expiresAtEpochMillis: number,
  ): Promise<string> {
    if (remoteArchiveRef.trim().length === 0) {
      throw new HttpsError("failed-precondition", "Export manifest is missing remoteArchiveRef.");
    }
    const file = adminStorage.bucket().file(remoteArchiveRef);
    const [exists] = await file.exists();
    if (!exists) {
      throw new HttpsError("failed-precondition", "Export archive is not available in storage.");
    }
    const [url] = await file.getSignedUrl({
      action: "read",
      expires: new Date(expiresAtEpochMillis),
    });
    return url;
  }
}
