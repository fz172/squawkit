package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable

/**
 * How this platform gets a newer build (`template_system_design.md` §6.3).
 *
 * Android and iOS deep-link to the store listing — an install. Web has no install step: the
 * deployed bundle updates on reload, so [isReload] tells the caller to say "reload" rather than
 * "update" and the action re-fetches the page.
 */
class AppUpdatePrompt(val isReload: Boolean, val launch: () -> Unit)

@Composable
expect fun rememberAppUpdatePrompt(): AppUpdatePrompt
