# Firebase Backend Health Probe

This is the minimal Firebase backend scaffold for WingsLog/Hopply.

It is intentionally small:

- one Firebase workspace at `backend/firebase/`
- one TypeScript Cloud Functions package at `backend/firebase/functions/`
- one callable function, `health_probe`

## Before first use

1. Install the Firebase CLI if you do not already have it.
2. Replace `your-firebase-project-id` in `.firebaserc` with the real project ID.
3. From `backend/firebase/functions/`, install dependencies.

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

The callable function name is `health_probe`.

## Production behavior

The callable is intentionally locked down:

- requires Firebase Authentication
- requires valid App Check tokens
- rejects calls from Firebase app IDs outside this repo's Android and iOS apps

That means a raw HTTP POST is not a valid production-style call path. Use a real Firebase client
SDK from the app once you want to test the production configuration end to end.

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

This deploys the callable into project `wingslog-9ca4e` from `.firebaserc`.

If you want this callable to be Android-only, remove the iOS app ID from
`functions/src/index.ts` before deploying.

## What this proves

- Firebase workspace layout is correct
- TypeScript function build works
- Functions emulator starts
- Deploy path is valid
- App can later call a callable function before export-email work begins
