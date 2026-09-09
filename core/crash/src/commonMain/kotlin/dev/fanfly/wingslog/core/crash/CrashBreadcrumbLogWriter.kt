package dev.fanfly.wingslog.core.crash

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

/**
 * Mirrors Kermit output into the Crashlytics breadcrumb trail, so a report arrives with the last
 * few hundred lines of app log attached instead of a bare stack.
 *
 * Info and above only, deliberately. Debug and verbose logs carry the identifiers `configureLogging`
 * keeps out of release builds — other accounts' uids, remote blob paths, signed URLs — and a
 * breadcrumb is exactly as visible as a release log. Gating here rather than relying on
 * `Logger.minSeverity` means a developer build, which does emit those, still does not ship them to
 * Crashlytics.
 *
 * A `Throwable` logged at Error also becomes a non-fatal report: those are the ones someone would
 * want to see aggregated, and the caller has already decided it is worth a log line.
 */
class CrashBreadcrumbLogWriter(
  private val crashReporter: CrashReporter,
) : LogWriter() {

  override fun isLoggable(tag: String, severity: Severity): Boolean =
    severity >= Severity.Info

  override fun log(
    severity: Severity,
    message: String,
    tag: String,
    throwable: Throwable?,
  ) {
    crashReporter.log("${severity.name.first()}/$tag: $message")
    if (throwable != null && severity >= Severity.Error) {
      crashReporter.recordException(throwable)
    }
  }
}
