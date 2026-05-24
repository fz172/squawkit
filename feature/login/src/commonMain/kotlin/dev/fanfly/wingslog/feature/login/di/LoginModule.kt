package dev.fanfly.wingslog.feature.login.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loginModule = module {
  viewModel { LoginViewModel(get<AuthManager>()) }
}
