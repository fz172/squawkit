package dev.fanfly.wingslog.core.auth.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.auth.AuthManagerImpl
import org.koin.core.module.Module
import org.koin.dsl.module
import dev.gitlive.firebase.auth.FirebaseAuth

actual val authModule: Module = module {
  single<AuthManager> { AuthManagerImpl(get<FirebaseAuth>()) }
}
