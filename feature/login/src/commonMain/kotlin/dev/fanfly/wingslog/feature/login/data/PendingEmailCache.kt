package dev.fanfly.wingslog.feature.login.data

/**
 * Cross-session cache of the email a passwordless sign-in link was issued for, kept OUTSIDE the
 * single-tab-locked local database.
 *
 * On web the local SQLite database lives in OPFS, which only one browser tab can open at a time (see
 * webApp `SingleTabGate`). But an email link opens in a *new* tab while the original tab still holds
 * that lock, so the completing tab can't read [EmailLinkStore]'s database stash. This cache is
 * backed by `localStorage` on web — shared across all tabs of the origin and readable without the
 * database — so leg 2 can complete in whichever tab opens the link.
 *
 * A no-op elsewhere: on Android and iOS the same app process completes the link, so
 * [EmailLinkStore]'s database stash already survives the app restart. See
 * docs/account/email_link_signin_design.html.
 */
expect object PendingEmailCache {
  fun save(email: String)
  fun clear()
  fun load(): String?
}
