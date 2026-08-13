package dev.fanfly.wingslog.core.auth

// Firebase's provider ids. These are Firebase's own wire values, not ours — they appear both when
// building a credential (OAuthProvider.credential) and when reading FirebaseUser.providerData, so
// they are declared once here and shared by every platform implementation in this module.

internal const val APPLE_PROVIDER_ID = "apple.com"

internal const val GOOGLE_PROVIDER_ID = "google.com"

/**
 * The scopes to request from Apple, shared by every platform that drives the flow itself.
 *
 * Both are asked for because Apple returns the user's name **only on the very first authorization**
 * of the app, and only when the scope was requested — get it wrong once and it is gone for that
 * Apple ID for good. Declared here so the web popup and the Android Custom Tab cannot drift apart on
 * something that is unrecoverable if they do.
 *
 * iOS is not a caller: `ASAuthorization` takes its own typed `[.fullName, .email]` in Swift.
 */
internal val APPLE_SCOPES = listOf("email", "name")
