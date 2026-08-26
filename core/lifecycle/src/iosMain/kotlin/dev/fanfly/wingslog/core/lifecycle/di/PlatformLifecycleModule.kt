package dev.fanfly.wingslog.core.lifecycle.di

import org.koin.core.module.Module
import org.koin.dsl.module

/** Nothing to register: there is no Activity here, and no auth flow that would need one. */
actual val platformLifecycleModule: Module = module {}
