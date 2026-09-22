package dev.fanfly.wingslog.core.ui.theme

import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual fun isReduceMotionEnabled(): Boolean =
  UIAccessibilityIsReduceMotionEnabled()
