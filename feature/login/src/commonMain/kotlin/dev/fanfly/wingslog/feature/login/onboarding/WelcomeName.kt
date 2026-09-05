package dev.fanfly.wingslog.feature.login.onboarding

/**
 * Picks the name the welcome screen greets, from the two places a just-signed-in user can carry one.
 *
 * The local technician record is the in-app source of truth, so it wins — but on a fresh
 * Google/Apple signup it does not exist yet (nothing has written it; the provider profile is all we
 * have), and on a returning user's new device it only appears once sync lands. The auth profile's
 * display name covers both gaps. Blank when neither has one, which only happens on paths that route
 * to name entry first.
 */
internal fun resolveWelcomeName(localSelfName: String?, accountName: String?): String =
  localSelfName.orEmpty().trim().ifBlank { accountName.orEmpty().trim() }
