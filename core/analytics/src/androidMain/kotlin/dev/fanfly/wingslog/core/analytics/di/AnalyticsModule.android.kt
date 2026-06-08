package dev.fanfly.wingslog.core.analytics.di

import com.google.firebase.analytics.FirebaseAnalytics
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.FirebaseAnalyticsManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> {
    FirebaseAnalyticsManager(FirebaseAnalytics.getInstance(androidContext()))
  }
}
