package dev.fanfly.wingslog.feature.userprofile.di

import dev.fanfly.wingslog.feature.userprofile.data.EditProfileViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val userProfileModule = module {
  viewModel { EditProfileViewModel(get(), get()) }
}
