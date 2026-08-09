package dev.fanfly.wingslog.core.lifecycle.di

import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The app-scoped lifecycle signal.
 *
 * Registered in `CommonAppModules.kt` **ahead of** `adsModule`: the ad session counter depends on
 * this and not the reverse. Koin resolves lazily, so order is not strictly required — but the list
 * is read by people, and the dependency direction should be visible there.
 *
 * [AppForegroundObserver] must be a `single`. Two instances would mean two independent notions of
 * "the session", and the ad cap would reset on whichever one happened to be injected.
 */
val lifecycleModule: Module = module {
  single { AppForegroundObserver() }
}
