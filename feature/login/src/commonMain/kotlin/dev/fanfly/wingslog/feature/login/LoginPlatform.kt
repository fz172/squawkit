package dev.fanfly.wingslog.feature.login

/**
 * Whether the anonymous ("Continue without account") sign-in option is offered on this platform.
 *
 * Disabled on web: a guest session can't be upgraded to a real account in the browser yet (see
 * jsMain `AuthManagerImpl.upgradeAnonymousAccount`), so web users are steered to a real account
 * up front rather than into a dead-end guest session.
 */
internal expect val isAnonymousLoginSupported: Boolean

/**
 * URL of the privacy notice opened from the login screen. On native platforms this is the
 * canonical hosted page; on web the page is served from the app's own origin.
 */
internal expect val privacyPolicyUrl: String
