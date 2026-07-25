package dev.fanfly.wingslog.feature.shell

import androidx.compose.ui.window.DialogProperties

actual fun formDialogProperties(): DialogProperties =
  DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
  )
