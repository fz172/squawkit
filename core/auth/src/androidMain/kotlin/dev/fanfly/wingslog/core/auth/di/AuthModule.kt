package dev.fanfly.wingslog.core.auth.di

import dev.fanfly.wingslog.core.auth.GitLiveAuthManager
import dev.fanfly.wingslog.core.auth.GitLiveAuthManagerImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val authModule = module {
  single<GitLiveAuthManager> { GitLiveAuthManagerImpl(androidContext(), get()) }
}
