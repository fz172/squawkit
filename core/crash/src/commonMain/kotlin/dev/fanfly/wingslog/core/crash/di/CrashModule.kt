package dev.fanfly.wingslog.core.crash.di

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.crash.CrashBreadcrumbLogWriter
import dev.fanfly.wingslog.core.crash.CrashReporter
import dev.fanfly.wingslog.core.crash.CrashUserIdBinder
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Provides `single<CrashReporter>`: Firebase Crashlytics on Android and iOS (one GitLive-backed
 * implementation shared by both, in `mobileMain`), a no-op on web.
 */
internal expect val platformCrashReporterModule: Module

/**
 * The one crash-reporting entry `commonAppModules` lists.
 *
 * Both singles are eager. The breadcrumb writer has to be installed before the logs worth keeping
 * are emitted, and the uid binder has to be subscribed before the first `authStateChanged` — a
 * silent sign-in during startup is exactly the window a crash would land in.
 */
val crashModule: Module = module {
  includes(platformCrashReporterModule)
  single(createdAtStart = true) {
    CrashBreadcrumbLogWriter(get<CrashReporter>()).also { Logger.addLogWriter(it) }
  }
  single(createdAtStart = true) {
    CrashUserIdBinder(get<FirebaseAuth>(), get<CrashReporter>())
  }
}
