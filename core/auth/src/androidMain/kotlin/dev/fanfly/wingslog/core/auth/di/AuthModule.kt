package dev.fanfly.wingslog.core.auth.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val authModule = module {
  single<AuthManager> { AuthManagerImpl(androidContext(), get()) }
}
