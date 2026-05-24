package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.onboarding.OnboardingPreferences
import org.koin.dsl.module

val appModule = module {
  single { OnboardingPreferences(db = get(), auth = get()) }
}
