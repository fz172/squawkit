package dev.fanfly.wingslog.core.analytics.di

import android.annotation.SuppressLint
import com.google.firebase.analytics.FirebaseAnalytics
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.AnalyticsPreferenceStore
import dev.fanfly.wingslog.core.analytics.AndroidAnalyticsPreferenceStore
import dev.fanfly.wingslog.core.analytics.FirebaseAnalyticsManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// FirebaseAnalytics needs INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK, which the Firebase
// measurement library merges into the consuming app's manifest at assembly time. This library
// module has no manifest of its own, so lint can't see them here — suppress the false positive.
@SuppressLint("MissingPermission")
actual val platformAnalyticsModule: Module = module {
  single<AnalyticsManager> {
    FirebaseAnalyticsManager(FirebaseAnalytics.getInstance(androidContext()))
  }
}

actual val analyticsPreferenceStoreModule: Module = module {
  single<AnalyticsPreferenceStore> { AndroidAnalyticsPreferenceStore(androidContext()) }
}
