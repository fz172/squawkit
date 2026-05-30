# Firebase Backend

This is the Firebase backend workspace for WingsLog/Hopply.

Milestone 0 is intentionally small but production-shaped:

- one Firebase workspace at `backend/firebase/`
- one TypeScript Cloud Functions package at `backend/firebase/functions/`
- two callable entrypoints:
  - `health_probe`
  - `requestExportDelivery`

## Runtime policy

- Cloud Functions 2nd gen
- Node.js `22` runtime in `functions/package.json`
- region fixed to `us-central1` in code
- deploys always build via `firebase.json` `predeploy`

## Before first use

1. Install the Firebase CLI if you do not already have it.
2. Replace `your-firebase-project-id` in `.firebaserc` with the real project ID.
3. From `backend/firebase/functions/`, install dependencies with `npm install`.
4. If you need local placeholder env values, copy `.env.example` to an untracked local file and edit that copy only.

## Local development

From `backend/firebase/functions/`:

```bash
npm install
npm run build
```

From `backend/firebase/`:

```bash
firebase emulators:start --only functions
```

Callable names:

- `health_probe`
- `requestExportDelivery`

## Production behavior

Both callables are intentionally locked down:

- requires Firebase Authentication
- requires valid App Check tokens
- rejects calls from Firebase app IDs outside this repo's Android and iOS apps

The allowed Firebase app IDs are enforced in `functions/src/shared/auth.ts`.

That means a raw HTTP POST is not a valid production-style call path. Use a real Firebase client
SDK from the app once you want to test the production configuration end to end.

## Export-delivery status

`requestExportDelivery(exportId)` is now the active Milestone 4 callable path.

- it validates auth, App Check, allowed app ID, and a non-empty `exportId`
- it loads `users/{uid}/export_history/{exportId}` from Firestore
- it acquires a short delivery lease to avoid duplicate concurrent sends
- it generates a signed download URL for the uploaded archive in Cloud Storage
- it sends mail through the configured provider and writes back `SENT` or `FAILED`

Provider credentials still belong only in runtime environment or Firebase-managed secrets, never in committed files.

Current provider contract:

- `EXPORT_DELIVERY_PROVIDER=resend`
- `EXPORT_DELIVERY_FROM_EMAIL=<verified sender>`

Keep `EXPORT_DELIVERY_PROVIDER` and `EXPORT_DELIVERY_FROM_EMAIL` as normal function env values.
Store `EXPORT_DELIVERY_API_KEY` in Google Cloud Secret Manager and bind it to
`requestExportDelivery`.

Set the secret once with the Firebase CLI:

```bash
firebase functions:secrets:set EXPORT_DELIVERY_API_KEY
```

Then deploy functions normally. Firebase will keep the secret in Secret Manager and mount it only
into functions that declare access to it.

To update the secret later, run the same command again and enter the new value:

```bash
firebase functions:secrets:set EXPORT_DELIVERY_API_KEY
```

Then redeploy so the latest secret version is picked up by the deployed function:

```bash
firebase deploy --only functions
```

## Android App Check requirements

Before production calls from Android will work, both sides must be enabled:

1. In Firebase Console, register the Android app under App Check and select the Play Integrity provider.
2. In Google Play Console, link the Play Integrity API to the same Google Cloud project as Firebase.
3. In Firebase Console, enable App Check enforcement for **Cloud Functions** after you confirm metrics look healthy.
4. In the Android app, initialize Firebase App Check before using other Firebase SDKs.

This repo's Android app now installs:

- `PlayIntegrityAppCheckProviderFactory` for non-debug builds
- `DebugAppCheckProviderFactory` for debug builds

For emulator or local debug testing, register the emitted debug token in Firebase Console:
App Check -> Android app -> Manage debug tokens.

## Deploy

From `backend/firebase/`:

```bash
firebase deploy --only functions
```

This deploys the callables into project `wingslog-9ca4e` from `.firebaserc`.

## Secrets policy

- Commit `.env.example` placeholders only.
- Keep real `.env`, `.env.*`, and service-account files out of git.
- Prefer Firebase Functions secret/env management for provider credentials.
- `EXPORT_DELIVERY_API_KEY` is now expected from Secret Manager, not `.env`.
- Never commit deployment credentials or mail-provider API keys.

## Storage bucket CORS

The web app (`webApp`) downloads attachment bytes by `fetch`ing the
`firebasestorage.googleapis.com` download URL returned by
`StorageReference.getDownloadUrl()`. Browsers enforce CORS on that request,
and the storage bucket ships with no CORS rules — so every download fails
with `TypeError: Failed to fetch` until the bucket is configured.

`storage_cors.json` in this directory is the minimal rule set: allow `GET`
from any origin (the download URL contains a per-object access token, so
the token is the auth — `*` is safe). Apply it once per environment with:

```bash
gcloud storage buckets update gs://wingslog-9ca4e.firebasestorage.app \
  --cors-file=backend/firebase/storage_cors.json
```

(or the equivalent `gsutil cors set` if you prefer the older CLI). Native
iOS and Android use the binary Firebase SDK protocol and don't need CORS.

## What this proves

- Firebase workspace layout is correct
- TypeScript function build works
- Functions emulator starts
- Deploy path is valid
- App can call a locked-down callable function boundary before export-email work begins
