package dev.fanfly.wingslog

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dev.fanfly.wingslog.core.database.infra.commonFirebaseModule
import dev.fanfly.wingslog.core.database.infra.firebaseModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.di.appModule
import dev.fanfly.wingslog.feature.aircraft.database.impl.aircraftDatabaseModule
import dev.fanfly.wingslog.feature.aircraft.di.aircraftModule
import dev.fanfly.wingslog.feature.fleet.database.impl.fleetDatabaseModule
import dev.fanfly.wingslog.feature.fleet.di.fleetModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.userprofile.database.impl.userProfileDatabaseModule
import dev.fanfly.wingslog.feature.userprofile.di.userProfileModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WingsLogApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // Initialize Firebase
    FirebaseApp.initializeApp(this)
    Log.d("WingsLogApplication", "Firebase initialized")

    startKoin {
      androidContext(this@WingsLogApplication)
      modules(
        commonFirebaseModule,
        firebaseModule,
        authModule,
        userProfileDatabaseModule,
        aircraftDatabaseModule,
        fleetDatabaseModule,
        appModule,
        userProfileModule,
        settingsModule,
        fleetModule,
        aircraftModule,
      )
    }
  }
}
