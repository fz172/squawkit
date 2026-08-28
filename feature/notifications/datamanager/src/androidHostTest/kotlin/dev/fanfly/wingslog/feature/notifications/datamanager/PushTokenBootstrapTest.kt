package dev.fanfly.wingslog.feature.notifications.datamanager

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The registration path that `onNewToken` alone cannot cover: a device that already holds a token.
 * Without this the fan-out reaches `sent: 0` on every such device, forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenBootstrapTest {

  private val forwarded = mutableListOf<String>()

  private fun bootstrap(
    scope: CoroutineScope,
    readToken: suspend () -> String?
  ) =
    PushTokenBootstrap(
      sink = { token -> forwarded += token },
      scope = scope,
      readToken = readToken,
    )

  @Test
  fun `forwards the token the device already holds`() = runTest {
    val scope = TestScope(testScheduler)
    bootstrap(scope) { "existing-token" }
    scope.runCurrent()

    assertThat(forwarded).containsExactly("existing-token")
  }

  @Test
  fun `forwards nothing when there is no token yet`() = runTest {
    val scope = TestScope(testScheduler)
    bootstrap(scope) { null }
    scope.runCurrent()

    assertThat(forwarded).isEmpty()
  }

  @Test
  fun `forwards nothing when the token is blank`() = runTest {
    val scope = TestScope(testScheduler)
    bootstrap(scope) { "" }
    scope.runCurrent()

    assertThat(forwarded).isEmpty()
  }

  /** FCM can fail offline. A throw here would take the whole Koin startup down with it. */
  @Test
  fun `survives a failure to read the token`() = runTest {
    val scope = TestScope(testScheduler)
    bootstrap(scope) { error("FCM unavailable") }
    scope.runCurrent()

    assertThat(forwarded).isEmpty()
  }

  /** The sink writes to Firestore; offline, that throws. Startup must not care. */
  @Test
  fun `survives a failure to forward the token`() = runTest {
    val scope = TestScope(testScheduler)
    PushTokenBootstrap(
      sink = { error("Firestore unavailable") },
      scope = scope,
      readToken = { "existing-token" },
    )
    scope.runCurrent()
  }
}
