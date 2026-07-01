package dev.fanfly.wingslog.feature.login.data

// No-op on Android: the same process completes the link, so EmailLinkStore's database stash suffices.
actual object PendingEmailCache {
  actual fun save(email: String) {}
  actual fun clear() {}
  actual fun load(): String? = null
}
