package dev.fanfly.wingslog.core.lifecycle.di

import android.app.Application
import dev.fanfly.wingslog.core.lifecycle.CurrentActivityProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformLifecycleModule: Module = module {
  // Eager, and a singleton, for two different reasons. Singleton: it registers the application's
  // activity lifecycle callbacks on construction, so a factory would add a fresh set on every
  // injection. Eager: built lazily, it would not exist until something first injected it — which is
  // long after MainActivity resumed, so it would miss that resume and report no foreground activity
  // until the user left the app and came back. startKoin runs in Application.onCreate, before any
  // activity exists, which is exactly when these callbacks need to be in place.
  single(createdAtStart = true) { CurrentActivityProvider(androidContext() as Application) }
}
