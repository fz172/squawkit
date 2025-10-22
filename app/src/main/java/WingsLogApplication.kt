package dev.fanfly.wingslog

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WingsLogApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    // Initialize Firebase
    FirebaseApp.initializeApp(this)
    Log.d("WingsLogApplication", "Firebase initialized")
  }

}