package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.onboarding.TechnicianOnboardingActions
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
  // OnboardingActions backs the shared AuthFlow's name step with the real TechnicianManager.
  // OnboardingPreferences (the hasSeenWelcome flag) now lives in feature/login's loginModule.
  single { TechnicianOnboardingActions(get()) } bind OnboardingActions::class
}
