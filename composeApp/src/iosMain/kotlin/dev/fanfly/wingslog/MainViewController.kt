package dev.fanfly.wingslog

import androidx.compose.ui.window.ComposeUIViewController
import dev.fanfly.wingslog.di.initKoin
import platform.UIKit.UIViewController

object MainEntry {
    fun mainViewController(): UIViewController = ComposeUIViewController {
        AppEntry()
    }

    fun doInitKoin() {
        initKoin {}
    }
}
