package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
import dev.fanfly.wingslog.feature.sync.data.blob.UrlSessionUploadScheduler
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

object MainEntry {
  fun mainViewController(): UIViewController = ComposeUIViewController {
    AppEntry()
  }

  fun doInitKoin() {
    initKoin {}
    KoinPlatform.getKoin().get<TombstoneGc>().runOnce()
  }

  fun doInitKoinDogfood() {
    initKoin(dogfoodExtensions = StressTestDogfoodExtensions()) {}
    KoinPlatform.getKoin().get<TombstoneGc>().runOnce()
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
