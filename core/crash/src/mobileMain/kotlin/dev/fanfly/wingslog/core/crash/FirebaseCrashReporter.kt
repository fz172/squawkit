package dev.fanfly.wingslog.core.crash

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.crashlytics.FirebaseCrashlytics

/**
 * Android and iOS [CrashReporter], backed by Firebase Crashlytics through the GitLive binding —
 * the same SDK on both hosts, so there is one implementation rather than two that drift.
 *
 * [log] and [recordException] deliberately do not log through Kermit: [CrashBreadcrumbLogWriter]
 * feeds this class *from* Kermit, and a log line here would feed straight back into it.
 */
class FirebaseCrashReporter(
  private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
  private val log = Logger.withTag("Crash")

  override fun recordException(throwable: Throwable) {
    crashlytics.recordException(throwable)
  }

  override fun log(message: String) {
    crashlytics.log(message)
  }

  override fun setUserId(userId: String?) {
    // Crashlytics has no "clear" call; the empty string is how it detaches the id on sign-out.
    crashlytics.setUserId(userId.orEmpty())
    log.i { "crash reporting user id ${if (userId == null) "cleared" else "set"}" }
  }

  override fun setCustomKey(key: String, value: String) {
    crashlytics.setCustomKey(key, value)
  }

  override fun setCrashCollectionEnabled(enabled: Boolean) {
    crashlytics.setCrashlyticsCollectionEnabled(enabled)
    log.i { "crash collection enabled: $enabled" }
  }
}
