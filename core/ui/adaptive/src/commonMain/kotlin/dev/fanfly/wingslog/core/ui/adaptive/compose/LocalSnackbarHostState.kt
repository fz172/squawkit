package dev.fanfly.wingslog.core.ui.adaptive.compose

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's snackbar host, for content composed inside a shell section. Dialog destinations
 * post cross-screen messages through the back-stack entry instead; a quick action runs inside the
 * shell entry, so that channel is the wrong shape. Null where no host has provided one (a
 * preview): callers treat that as a silent no-op.
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
