package dev.fanfly.wingslog.core.auth.di

import android.app.Application
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthManagerImpl
import dev.fanfly.wingslog.core.auth.CurrentActivityProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val authModule = module {
  // Eager, and a singleton, for two different reasons. Singleton: it registers the application's
  // activity lifecycle callbacks on construction, so a factory would add a fresh set on every
  // injection. Eager: lazily, it is not built until something first injects AuthManager — which is
  // long after MainActivity resumed, so it would miss that resume and report no foreground activity
  // until the user left the app and came back. startKoin runs in Application.onCreate, before any
  // activity exists, which is exactly when these callbacks need to be in place.
  single(createdAtStart = true) { CurrentActivityProvider(androidContext() as Application) }
  single<AuthManager> {
    AuthManagerImpl(androidContext(), get<FirebaseAuth>(), get<CurrentActivityProvider>())
  }
}
