package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.core.auth.IosGoogleSignInBridge
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

object MainEntry {
  fun mainViewController(): UIViewController = ComposeUIViewController {
    AppEntry()
  }

  fun doInitKoin() {
    initKoin {}
    runTombstoneGc()
  }

  fun doInitKoinDogfood() {
    initKoin(dogfoodExtensions = StressTestDogfoodExtensions()) {}
    runTombstoneGc()
  }

  fun installGoogleSignInHandler(handler: () -> Unit) {
    IosGoogleSignInBridge.install(handler)
  }

  fun completeGoogleSignIn(errorMessage: String?) {
    IosGoogleSignInBridge.complete(errorMessage)
  }

  fun startSyncEngine() {
    KoinPlatform.getKoin().get<SyncEngine>().start()
  }

  // Best-effort startup GC; runOnce() is now suspend (async-generated queries).
  private fun runTombstoneGc() {
    CoroutineScope(Dispatchers.Default).launch {
      KoinPlatform.getKoin().get<TombstoneGc>().runOnce()
    }
  }

  /**
   * Registers the background blob-scan [BGProcessingTask] with the OS. Must be called before
   * `application:didFinishLaunchingWithOptions:` returns. The task identifier
   * `dev.fanfly.wingslog.blob-scan` must appear in `Info.plist`'s
   * `BGTaskSchedulerPermittedIdentifiers`.
   */
  fun registerBgTasks() {
    KoinPlatform.getKoin().get<UrlSessionUploadScheduler>().registerBgTasks()
  }
}
