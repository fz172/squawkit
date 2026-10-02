package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.functions.FirebaseFunctionsException

/**
 * A string field of a callable error's `details`, the object a function passes as the third
 * argument of `HttpsError`. The AI callables put their refusal code there (`details.code`,
 * docs/ai/task_population_design.md §5.3), because the gRPC status alone cannot tell
 * `daily_limit` from `spend_ceiling`.
 *
 * Platform-specific because each SDK hands `details` over as its own type: a `Map` on Android, an
 * `NSDictionary` on iOS, a plain object on the web. Null when there are no details, the field is
 * absent, or it is not a string.
 */
expect fun FirebaseFunctionsException.detailsString(key: String): String?

/** The call-site form, for a throwable that may not be a callable error at all. */
fun Throwable.callableDetailsString(key: String): String? =
  (this as? FirebaseFunctionsException)?.detailsString(key)
