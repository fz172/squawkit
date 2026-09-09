package dev.fanfly.wingslog.core.crash.di

import dev.fanfly.wingslog.core.crash.CrashReporter
import dev.fanfly.wingslog.core.crash.NoOpCrashReporter
import org.koin.core.module.Module
import org.koin.dsl.module

// Firebase ships no Crashlytics SDK for the web, so the breadcrumb writer and the uid binder run
// against a sink that discards everything rather than the web host having to skip crashModule.
internal actual val platformCrashReporterModule: Module = module {
  single<CrashReporter> { NoOpCrashReporter }
}
