package dev.fanfly.wingslog.core.appinfo

import androidx.compose.runtime.Composable

/**
 * "Rate SquawkIt": the store's in-product review flow, presented over the app rather than by
 * bouncing the pilot out to the listing — Play In-App Review on Android, StoreKit's
 * `requestReview` on iOS.
 *
 * Both stores treat the request as a hint: each decides for itself whether the card actually
 * appears (Play quota-limits it; Apple caps it at three a year per device) and neither reports
 * which way it went. So [launch] is best-effort by design. What it does guarantee is that a flow
 * the platform *refuses* outright — no store on the device, a sideloaded build — lands on the
 * listing's write-a-review page instead of doing nothing.
 */
class AppReviewPrompt(val launch: () -> Unit)

/** Null where there is no store to rate in (web), which hides the row. */
@Composable
expect fun rememberAppReviewPrompt(): AppReviewPrompt?
