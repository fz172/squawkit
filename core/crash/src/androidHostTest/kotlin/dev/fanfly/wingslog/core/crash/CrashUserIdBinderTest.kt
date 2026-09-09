package dev.fanfly.wingslog.core.crash

import com.google.common.truth.Truth.assertThat
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CrashUserIdBinderTest {
  private val authState = MutableStateFlow<FirebaseUser?>(null)
  private val auth = mockk<FirebaseAuth> {
    every { authStateChanged } returns authState
  }
  private val reporter = RecordingCrashReporter()

  @Test
  fun `the uid follows sign-in and is cleared on sign-out`() = runTest {
    CrashUserIdBinder(auth, reporter, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    authState.value = mockk<FirebaseUser> { every { uid } returns "uid-1" }
    authState.value = null

    assertThat(reporter.userIds).containsExactly(null, "uid-1", null)
      .inOrder()
  }
}
