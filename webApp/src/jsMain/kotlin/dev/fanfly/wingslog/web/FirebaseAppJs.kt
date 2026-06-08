@file:JsModule("firebase/app")
@file:JsNonModule

package dev.fanfly.wingslog.web

// Direct binding to the modular Firebase JS app SDK. We initialize the default app ourselves
// (instead of GitLive's Firebase.initialize) so the config can include measurementId, which is
// required for Firebase Analytics but not exposed by GitLive's FirebaseOptions. GitLive's auth /
// firestore / storage resolve this same default app via getApp().
external fun initializeApp(options: dynamic): dynamic
