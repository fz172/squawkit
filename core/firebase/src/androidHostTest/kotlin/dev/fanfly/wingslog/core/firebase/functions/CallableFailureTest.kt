package dev.fanfly.wingslog.core.firebase.functions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins which callable failures become Crashlytics non-fatals.
 *
 * This is a noise budget as much as a correctness property: Error severity carrying a throwable is
 * what `CrashBreadcrumbLogWriter` forwards, so every status promoted here costs dashboard signal,
 * and every status demoted risks another fault shipping unseen the way #951's two did.
 *
 * Exercised through the status-name form on purpose. `FunctionsExceptionCode` is a typealias to the
 * platform SDK's enum, and Android's throws `NoClassDefFoundError` on class-initialisation in a host
 * test — which is precisely why the rule takes a name.
 */
class CallableFailureTest {

  @Test
  fun `a failure that never reached the wire is always ours`() {
    // The shape of the serialization bug: no FirebaseFunctionsException, so no status at all. It
    // produced one Warn line and no report, which is how it survived to reach a user.
    assertThat(isCallableClientDefect(null)).isTrue()
  }

  @Test
  fun `a build the server will not attest is ours`() {
    // App Check declining the build. Nothing the user can do, and it does not self-heal.
    assertThat(isCallableClientDefect("UNAUTHENTICATED")).isTrue()
  }

  @Test
  fun `the network being the network is not a bug report`() {
    // The Android SDK folds every network error into INTERNAL, so these three are indistinguishable
    // from a subway ride. Reporting them would bury everything else.
    assertThat(isCallableClientDefect("INTERNAL")).isFalse()
    assertThat(isCallableClientDefect("UNAVAILABLE")).isFalse()
    assertThat(isCallableClientDefect("DEADLINE_EXCEEDED")).isFalse()
  }

  @Test
  fun `refusals a caller is expected to handle are not bugs`() {
    // Ordinary traffic: an invalid or spent promo code, a throttled caller, an account already
    // subscribed, a guest. All have user-facing copy and none is a defect.
    assertThat(isCallableClientDefect("NOT_FOUND")).isFalse()
    assertThat(isCallableClientDefect("RESOURCE_EXHAUSTED")).isFalse()
    assertThat(isCallableClientDefect("FAILED_PRECONDITION")).isFalse()
    assertThat(isCallableClientDefect("PERMISSION_DENIED")).isFalse()
  }

  @Test
  fun `a plain throwable carries no status, so it classifies as ours`() {
    // The extension form used at every call site.
    assertThat(RuntimeException("boom").isCallableClientDefect()).isTrue()
  }
}
