package dev.fanfly.wingslog.core.crash

import kotlin.experimental.ExperimentalNativeApi

/**
 * Records the Kotlin exception before Kotlin/Native tears the process down.
 *
 * Crashlytics sees the resulting trap either way, but the report it builds from it is a native
 * stack with no exception type and no message — the two things that make a KMP crash diagnosable.
 * Recording a non-fatal first attaches both. Delivery is best-effort: the write races the process
 * exit, and the fatal report is what survives if it loses.
 *
 * [terminateWithUnhandledException] then restores the default ending — same abort, same native
 * report — instead of letting the hook swallow a crash the app cannot continue past.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual fun installUnhandledExceptionHook(crashReporter: CrashReporter) {
  setUnhandledExceptionHook { throwable ->
    crashReporter.recordException(throwable)
    terminateWithUnhandledException(throwable)
  }
}
