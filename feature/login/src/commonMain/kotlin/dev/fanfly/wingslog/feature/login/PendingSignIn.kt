package dev.fanfly.wingslog.feature.login

import dev.fanfly.wingslog.feature.login.email.EmailSignInScreen

/**
 * Which sign-in request is awaiting a result, so only that row shows progress while the rest are
 * locked. Null means idle.
 *
 * Not the list of login methods on offer — only those that suspend *here*. The email option
 * navigates away to `EmailSignInScreen`, which owns the progress state for both legs of the link
 * flow, so it never reaches an in-flight state on this screen.
 */
internal enum class PendingSignIn { Google, Apple, Anonymous }
