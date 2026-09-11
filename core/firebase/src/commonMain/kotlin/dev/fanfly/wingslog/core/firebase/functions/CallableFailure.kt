package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code

/**
 * Whether a failed callable means *this build is broken*, rather than the network being the network
 * or the server declining something the caller did.
 *
 * Lives here, beside [FUNCTIONS_REGION], because it is a property of calling Cloud Functions and not
 * of any one feature: every callable client in the app faces the same question, and answering it
 * differently in each would make the resulting dashboard meaningless.
 *
 * ## Why the distinction has to exist
 *
 * `CrashBreadcrumbLogWriter` turns any Kermit log at Error carrying a throwable into a Crashlytics
 * non-fatal. That makes the choice of severity the choice of what gets aggregated — so logging every
 * failure at Error would fill the dashboard with offline users and bury the reports worth reading,
 * while logging none at Error is what let two real faults ship unnoticed (#951: a missing
 * serialization plugin, and an unregistered App Check debug token).
 *
 * ## The rule
 *
 * - **No callable status at all** ([statusName] `null` — the throwable never became a
 *   `FirebaseFunctionsException`). The request failed before the wire: a serialization fault, a
 *   programming error. Always ours. This is the one that caught the missing serialization plugin.
 * - **`UNAUTHENTICATED`.** The server would not accept this copy of the app — App Check could not
 *   attest it. Nothing the user can do and nothing that self-heals; it means this build, or the
 *   project's App Check configuration, is wrong. This is the one that caught the unregistered debug
 *   token.
 *
 * Everything else stays a warning, deliberately:
 *
 * - `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL` — the Android SDK folds *every* network error
 *   into `INTERNAL`, so treating these as defects would file a report for every subway ride.
 * - `NOT_FOUND`, `RESOURCE_EXHAUSTED`, `FAILED_PRECONDITION`, `PERMISSION_DENIED` — outcomes a
 *   caller is expected to handle: an invalid code, a throttled caller, an unmet precondition, a
 *   guest. Normal traffic, not bugs.
 *
 * The cost of that conservatism is real and worth naming: a genuine server fault arriving as
 * `INTERNAL` is indistinguishable here from a dropped connection, so it will not be reported. The
 * alternative — inspecting the exception's cause for an `IOException` — needs a JVM type this
 * common-source classifier cannot see, and guessing from class names would be worse than the gap.
 *
 * ## Why a status *name* rather than the enum
 *
 * `FunctionsExceptionCode` is a typealias to each platform's own SDK enum, and Android's cannot even
 * class-initialise in a host test — so a signature taking it would be untestable, which for a rule
 * whose whole job is deciding what gets reported is the wrong trade. The names are the canonical
 * gRPC status names and are identical on all three platforms, so the string is as stable as the
 * enum and costs nothing.
 */
fun isCallableClientDefect(statusName: String?): Boolean =
  statusName == null || statusName == UNAUTHENTICATED_STATUS

/** The call-site form: classify a caught throwable directly. */
fun Throwable.isCallableClientDefect(): Boolean =
  isCallableClientDefect((this as? FirebaseFunctionsException)?.code?.name)

private const val UNAUTHENTICATED_STATUS = "UNAUTHENTICATED"
