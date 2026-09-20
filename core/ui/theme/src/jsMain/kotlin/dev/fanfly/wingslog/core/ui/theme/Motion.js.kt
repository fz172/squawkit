package dev.fanfly.wingslog.core.ui.theme

import kotlinx.browser.window

internal actual fun isReduceMotionEnabled(): Boolean =
  window.matchMedia("(prefers-reduced-motion: reduce)").matches
