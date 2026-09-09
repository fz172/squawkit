package dev.fanfly.wingslog.core.crash

/**
 * Installs whatever the platform needs so an uncaught exception reaches Crashlytics with its Kotlin
 * type, message, and stack intact. Called once, when the reporter is built.
 */
internal expect fun installUnhandledExceptionHook(crashReporter: CrashReporter)
