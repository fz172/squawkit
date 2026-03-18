package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.login.data.LoginViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
  viewModel { LoginViewModel(get()) }
}
