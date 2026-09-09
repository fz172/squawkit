package dev.fanfly.wingslog.core.crash

import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the Crashlytics user id following the signed-in account for the life of the process.
 *
 * Without it a report says only that *someone* crashed, which is no use when a pilot writes in
 * about it. `authStateChanged` also covers sign-out — the id is cleared rather than left pointing
 * at whoever used the device last.
 */
class CrashUserIdBinder(
  auth: FirebaseAuth,
  crashReporter: CrashReporter,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
  init {
    scope.launch {
      auth.authStateChanged.collect { user -> crashReporter.setUserId(user?.uid) }
    }
  }
}
