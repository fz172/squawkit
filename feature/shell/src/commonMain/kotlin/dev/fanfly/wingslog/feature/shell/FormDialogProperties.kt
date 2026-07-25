package dev.fanfly.wingslog.feature.shell

import androidx.compose.ui.window.DialogProperties

/**
 * [DialogProperties] for the full-screen ("usePlatformDefaultWidth = false") form dialogs shared
 * across hosts. Android additionally sets `decorFitsSystemWindows = false` — required alongside
 * `usePlatformDefaultWidth = false` per Compose's own docs to avoid the dialog window's soft input
 * mode falling back to `SOFT_INPUT_ADJUST_UNSPECIFIED`, which lets the legacy OS window-pan fight
 * with our `imePadding()`-driven layout when the IME opens (#332). That param only exists on the
 * Android `actual` of `DialogProperties`, so it can't be referenced from commonMain directly.
 */
expect fun formDialogProperties(): DialogProperties
