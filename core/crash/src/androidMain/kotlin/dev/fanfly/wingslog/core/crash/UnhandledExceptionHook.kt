package dev.fanfly.wingslog.core.crash

/**
 * Nothing to install. The Crashlytics SDK replaces the JVM's default uncaught-exception handler
 * when Firebase initializes, and a Kotlin `Throwable` is already a JVM one — the report arrives
 * with the real type and stack without help.
 */
internal actual fun installUnhandledExceptionHook(crashReporter: CrashReporter) =
  Unit
