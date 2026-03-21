package dev.fanfly.wingslog.core.auth.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.gitlive.firebase.auth.FirebaseUser
import org.koin.core.module.Module
import org.koin.dsl.module

class AuthManagerIosStub : AuthManager {
    override fun getCurrentUser(): FirebaseUser? = null
    override suspend fun trySilentLogin(): FirebaseUser? = null
    override suspend fun signInWithGoogle(): FirebaseUser? = null
    override suspend fun logOut() {}
}

actual val authModule: Module = module {
    single<AuthManager> { AuthManagerIosStub() }
}
