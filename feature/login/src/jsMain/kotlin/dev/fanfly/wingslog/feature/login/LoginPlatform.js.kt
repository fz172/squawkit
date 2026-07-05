package dev.fanfly.wingslog.feature.login

// Web has no in-place guest upgrade path yet, so don't offer anonymous sign-in here.
internal actual val isAnonymousLoginSupported: Boolean = false

internal actual val isAppleSignInSupported: Boolean = true

// Served as a static page from the web app's own origin.
internal actual val privacyPolicyUrl: String = "/privacy.html"
