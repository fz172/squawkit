package dev.fanfly.wingslog.core.lifecycle.di

import org.koin.core.module.Module

/**
 * Lifecycle bindings that only some platforms have.
 *
 * Today that is Android's `CurrentActivityProvider` — Sign in with Apple runs as a Custom Tab there
 * and needs a foreground `Activity` (#408). iOS and web have nothing to contribute: their auth
 * flows are presented by the platform or the browser, so both actuals are deliberately empty.
 *
 * Separate from [lifecycleModule] rather than folded into it, so the shared `AppForegroundObserver`
 * registration — and the reason it must be a `single` — does not have to be copied into three
 * actuals to accommodate one platform-specific binding.
 */
expect val platformLifecycleModule: Module
