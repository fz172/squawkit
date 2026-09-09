package dev.fanfly.wingslog.core.crash

/**
 * Cross-platform crash and non-fatal reporting sink. Backed by Firebase Crashlytics on Android and
 * iOS; the web host has no Crashlytics SDK, so `jsMain` binds [NoOpCrashReporter].
 *
 * Fatal crashes need no call here — the SDK installs its own handlers at
 * `FirebaseApp.initializeApp` / `FirebaseApp.configure()` time. This interface covers the parts the
 * SDK cannot see by itself: non-fatals worth knowing about ([recordException]), the breadcrumb trail
 * that gets attached to whatever report comes next ([log], fed by [CrashBreadcrumbLogWriter]), and
 * the state that makes a report legible ([setUserId], [setCustomKey]).
 */
interface CrashReporter {
  /** Records a non-fatal. The app keeps running; the report is uploaded on the next launch. */
  fun recordException(throwable: Throwable)

  /**
   * Adds a breadcrumb to the next fatal, non-fatal, or ANR report. Newlines are stripped by the SDK
   * and the buffer rolls at 64 KB.
   */
  fun log(message: String)

  /**
   * Associates subsequent reports with the signed-in uid, or clears it on sign-out. This is the
   * user's *own* uid — the one identifier the log-privacy rule has never asked us to redact.
   */
  fun setUserId(userId: String?)

  /** Attaches a key/value pair to subsequent reports. Crashlytics caps this at 64 pairs. */
  fun setCustomKey(key: String, value: String)

  /**
   * Turns crash collection on or off, following the "Help Us Improve" preference alongside
   * analytics collection (see `AnalyticsPreferenceController`).
   *
   * Asymmetric by design, and the SDK's own semantics: turning it *off* takes effect on the next
   * run of the app, so reports already on disk stay there until [CrashReporter] is asked again.
   */
  fun setCrashCollectionEnabled(enabled: Boolean)
}

/** No-op used on web and wherever a reporter is not worth constructing (tests, previews). */
object NoOpCrashReporter : CrashReporter {
  override fun recordException(throwable: Throwable) = Unit

  override fun log(message: String) = Unit

  override fun setUserId(userId: String?) = Unit

  override fun setCustomKey(key: String, value: String) = Unit

  override fun setCrashCollectionEnabled(enabled: Boolean) = Unit
}
