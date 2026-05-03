package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.core.storage.TombstoneGc
import dev.fanfly.wingslog.di.initKoin
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
}
