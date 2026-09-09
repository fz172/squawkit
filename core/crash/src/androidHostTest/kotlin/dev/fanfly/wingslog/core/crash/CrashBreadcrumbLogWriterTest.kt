package dev.fanfly.wingslog.core.crash

import co.touchlab.kermit.Severity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashBreadcrumbLogWriterTest {
  private val reporter = RecordingCrashReporter()
  private val writer = CrashBreadcrumbLogWriter(reporter)

  @Test
  fun `debug and verbose never become breadcrumbs`() {
    // The severities configureLogging keeps out of release builds are the ones carrying identifiers
    // that must not reach Crashlytics — a developer build emits them, and this is what stops them.
    assertThat(writer.isLoggable("Sync", Severity.Verbose)).isFalse()
    assertThat(writer.isLoggable("Sync", Severity.Debug)).isFalse()
    assertThat(writer.isLoggable("Sync", Severity.Info)).isTrue()
    assertThat(writer.isLoggable("Sync", Severity.Error)).isTrue()
  }

  @Test
  fun `a breadcrumb carries the severity and tag`() {
    writer.log(Severity.Warn, "upload retried", "Blob", null)

    assertThat(reporter.breadcrumbs).containsExactly("W/Blob: upload retried")
  }

  @Test
  fun `a throwable logged at error is also recorded as a non-fatal`() {
    val failure = IllegalStateException("no scope")

    writer.log(Severity.Error, "scope resolution failed", "Scope", failure)

    assertThat(reporter.exceptions).containsExactly(failure)
  }

  @Test
  fun `a throwable below error stays a breadcrumb only`() {
    writer.log(Severity.Warn, "retrying", "Blob", IllegalStateException("transient"))

    assertThat(reporter.exceptions).isEmpty()
    assertThat(reporter.breadcrumbs).hasSize(1)
  }
}
