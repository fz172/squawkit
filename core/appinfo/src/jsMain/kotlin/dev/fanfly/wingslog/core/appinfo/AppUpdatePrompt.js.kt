package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
actual fun rememberAppUpdatePrompt(): AppUpdatePrompt = remember {
  // No install step on web — the deployed bundle arrives on reload (design §6.3).
  AppUpdatePrompt(isReload = true) { window.location.reload() }
}
