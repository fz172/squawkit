package dev.fanfly.wingslog.core.auth.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import org.koin.core.module.Module
import org.koin.dsl.module

expect val authModule: Module

val commonAuthModule = module {
  // Multiplatform (GitLive) SDK Instances
  single<FirebaseAuth> { Firebase.auth }
}