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

## Deploy

From `backend/firebase/`:

```bash
firebase deploy --only functions
```

## What this proves

- Firebase workspace layout is correct
- TypeScript function build works
- Functions emulator starts
- Deploy path is valid
- App can later call a callable function before export-email work begins
