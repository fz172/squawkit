package dev.fanfly.wingslog.core.auth.di

import dev.fanfly.wingslog.core.auth.AccountDeleter
import dev.fanfly.wingslog.core.auth.impl.FirebaseAccountDeleter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.functions.FirebaseFunctions
import org.koin.core.module.Module
import org.koin.dsl.module

/** Binds `AuthManager` per host: the Android actual needs the current Activity for sign-in UI. */
internal expect val platformAuthModule: Module

/** The one auth entry `commonAppModules` lists: shared GitLive SDK bindings + the platform actual. */
val authModule: Module = module {
  includes(platformAuthModule)
  single<FirebaseAuth> { Firebase.auth }
  single<AccountDeleter> { FirebaseAccountDeleter(get<FirebaseFunctions>()) }
}
