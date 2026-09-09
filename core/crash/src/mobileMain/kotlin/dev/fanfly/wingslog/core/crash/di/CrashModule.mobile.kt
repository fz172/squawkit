package dev.fanfly.wingslog.core.crash.di

import dev.fanfly.wingslog.core.crash.CrashReporter
import dev.fanfly.wingslog.core.crash.FirebaseCrashReporter
import dev.fanfly.wingslog.core.crash.installUnhandledExceptionHook
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import org.koin.core.module.Module
import org.koin.dsl.module

// Firebase is reached inside the definition, never while the module is declared: iOS configures the
// default FirebaseApp in the SwiftUI @main init, which runs before doInitKoin but after this file's
// top level.
internal actual val platformCrashReporterModule: Module = module {
  single<CrashReporter> {
    FirebaseCrashReporter(Firebase.crashlytics).also(::installUnhandledExceptionHook)
  }
}
