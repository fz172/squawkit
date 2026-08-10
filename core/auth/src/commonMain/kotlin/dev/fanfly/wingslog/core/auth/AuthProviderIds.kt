package dev.fanfly.wingslog.core.auth

// Firebase's provider ids. These are Firebase's own wire values, not ours — they appear both when
// building a credential (OAuthProvider.credential) and when reading FirebaseUser.providerData, so
// they are declared once here and shared by every platform implementation in this module.

internal const val APPLE_PROVIDER_ID = "apple.com"

internal const val GOOGLE_PROVIDER_ID = "google.com"
