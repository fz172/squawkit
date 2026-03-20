package dev.fanfly.wingslog

import android.app.Application
import co.touchlab.kermit.Logger

import dev.fanfly.wingslog.di.initKoin
import org.koin.android.ext.koin.androidContext

class WingsLogApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // Firebase is initialized implicitly by the google-services plugin on Android.
    Logger.d("WingsLogApplication") { "WingsLogApplication started" }

    initKoin {
      androidContext(this@WingsLogApplication)
    }
  }
}
