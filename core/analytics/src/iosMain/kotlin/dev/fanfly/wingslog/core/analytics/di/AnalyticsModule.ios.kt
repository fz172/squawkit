package dev.fanfly.wingslog.core.analytics.di

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.FirebaseAnalyticsManager
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> { FirebaseAnalyticsManager(Firebase.analytics) }
}
