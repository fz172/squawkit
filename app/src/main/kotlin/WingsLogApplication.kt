package dev.fanfly.wingslog

import android.app.Application
import android.content.pm.ApplicationInfo
import co.touchlab.kermit.Logger
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class WingsLogApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    initializeFirebaseAppCheck()
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

  private fun initializeFirebaseAppCheck() {
    FirebaseApp.initializeApp(this)
    val providerFactory = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      DebugAppCheckProviderFactory.getInstance()
    } else {
      PlayIntegrityAppCheckProviderFactory.getInstance()
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(providerFactory)
  }
}
