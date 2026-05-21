import { getApp, getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

function adminApp() {
  return getApps().length > 0 ? getApp() : initializeApp();
}

export const adminDb = getFirestore(adminApp());
export const adminStorage = getStorage(adminApp());
