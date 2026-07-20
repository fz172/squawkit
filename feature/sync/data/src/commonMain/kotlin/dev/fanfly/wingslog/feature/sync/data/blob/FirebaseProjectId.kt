package dev.fanfly.wingslog.feature.sync.data.blob

/**
 * The Firebase project id, used to build the HTTP functions base URL for the `streamBlob` download
 * proxy. Platform-specific because gitlive's `Firebase.app.options` getter NPEs on iOS; Android and
 * JS read it from gitlive, iOS reads `GoogleService-Info.plist` directly.
 */
internal expect fun firebaseProjectId(): String?
