package dev.fanfly.wingslog.core.auth.di

import android.app.Application
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthManagerImpl
import dev.fanfly.wingslog.core.auth.CurrentActivityProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val authModule = module {
  // Must be a singleton: it registers the application's activity lifecycle callbacks on
  // construction, so a factory would add a fresh set on every injection.
  single { CurrentActivityProvider(androidContext() as Application) }
  single<AuthManager> {
    AuthManagerImpl(androidContext(), get<FirebaseAuth>(), get<CurrentActivityProvider>())
  }
}
