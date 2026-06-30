package dev.fanfly.wingslog.feature.login.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.login.data.EmailLinkStore
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingPreferences
import dev.fanfly.wingslog.feature.login.onboarding.TechnicianOnboardingActions
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loginModule = module {
  viewModel { LoginViewModel(get<AuthManager>(), get<EmailLinkStore>()) }

  single { EmailLinkStore(get<WingsLogDatabase>(), get<DatabaseWriteLock>()) }

  single {
    OnboardingPreferences(
      get<WingsLogDatabase>(),
      get<FirebaseAuth>(),
      get<DatabaseWriteLock>(),
    )
  }
  single<OnboardingActions> { TechnicianOnboardingActions(get<TechnicianManager>()) }
}
