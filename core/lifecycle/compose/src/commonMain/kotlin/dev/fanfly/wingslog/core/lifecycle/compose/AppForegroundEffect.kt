package dev.fanfly.wingslog.core.lifecycle.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver

/**
 * Drives [AppForegroundObserver] from Compose. The other half of the split described on that class:
 * it holds the state machine, this supplies the edges.
 *
 * Lives in `core:lifecycle:compose` rather than `core:lifecycle` so the parent module stays
 * Compose-free. `feature/ads/datamanager` depends on the parent, and AGENTS.md forbids a
 * `datamanager` depending on UI — Compose in the parent would make that violation transitive, which
 * is the least visible way to break a layering rule.
 *
 * **Call this exactly once, from the app root** — `AppEntry()` on Android/iOS and `WebApp()` on the
 * web host — never from a screen or a navigation destination.
 *
 * That placement is load-bearing rather than tidiness. A nav destination is *disposed* when the user
 * navigates away from it, so an effect installed there would report a background on every trip into
 * a form or a detail sheet. Harmless for a short visit, but a pilot who spent half an hour writing
 * up a squawk would come back to a falsely-new session and a reset ad budget. The app root stays
 * composed for the process lifetime, so its edges are real ones.
 *
 * One primitive covers all three hosts — `LifecycleResumeEffect` maps onto UIKit foreground
 * notifications on iOS and `document.visibilitychange` on web — which is why this is five lines here
 * instead of three per-host `expect`/`actual` implementations over `ProcessLifecycleOwner`,
 * `NSNotificationCenter` and the DOM.
 */
@Composable
fun AppForegroundEffect(observer: AppForegroundObserver) {
  LifecycleResumeEffect(observer) {
    observer.onEnterForeground()
    onPauseOrDispose { observer.onEnterBackground() }
  }
}
