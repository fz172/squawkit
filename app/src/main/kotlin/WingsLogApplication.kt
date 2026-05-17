package dev.fanfly.wingslog

import android.app.Application
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class WingsLogApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // Firebase is initialized implicitly by the google-services plugin on Android.
    logger.d { "WingsLogApplication started" }

    initKoin(dogfoodExtensions = createDogfoodExtensions()) {
      androidContext(this@WingsLogApplication)
    }
    get<TombstoneGc>().runOnce()
    get<SyncEngine>().start()
  }

  companion object {
    private val logger = Logger.withTag("WingsLogApplication")
  }
}
