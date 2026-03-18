package dev.fanfly.wingslog.core.network.di

import dev.fanfly.wingslog.core.network.auth.AuthManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val networkModule = module {
  single { AuthManager(androidContext(), get()) }
}
