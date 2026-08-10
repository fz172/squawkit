package dev.fanfly.wingslog.feature.login.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `AuthFlow` waits on this flag before acting on Firebase's `authStateChanged`, so a sign-in that
 * is still finishing here cannot be advanced past by that listener.
 *
 * The case that forced it is Sign in with Apple: Apple returns the display name alongside the
 * authorization rather than inside the credential, so `signInWithApple()` writes it in a second
 * round trip *after* Firebase has already reported a signed-in user. Advancing on that early
 * report reads an empty profile and sends a user who just told Apple their name to the name-entry
 * step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelSignInInFlightTest {

  private val authManager: AuthManager = mockk()
  private val emailLinkStore: EmailLinkStore = mockk(relaxed = true)
  private val viewModel = LoginViewModel(authManager, emailLinkStore)

  @Test
  fun signInInFlight_isFalseBeforeAnySignIn() {
    assertThat(viewModel.signInInFlight.value).isFalse()
  }

  @Test
  fun loginWithApple_holdsTheFlagForTheWholeCallIncludingTheProfileWrite() = runTest {
    var flagDuringCall: Boolean? = null
    coEvery { authManager.signInWithApple() } coAnswers {
      // Stands in for the window where Firebase has accepted the credential but
      // captureAppleName has not yet pushed the display name.
      flagDuringCall = viewModel.signInInFlight.value
      mockk<FirebaseUser>()
    }

    viewModel.loginWithApple()

    assertThat(flagDuringCall).isTrue()
    assertThat(viewModel.signInInFlight.value).isFalse()
  }

  @Test
  fun loginWithApple_clearsTheFlagWhenTheProviderThrows() = runTest {
    coEvery { authManager.signInWithApple() } throws IllegalStateException("boom")

    runCatching { viewModel.loginWithApple() }

    assertThat(viewModel.signInInFlight.value).isFalse()
  }

  @Test
  fun login_alsoTracksInFlight() = runTest {
    var flagDuringCall: Boolean? = null
    coEvery { authManager.signInWithGoogle() } coAnswers {
      flagDuringCall = viewModel.signInInFlight.value
      null
    }

    viewModel.login()

    assertThat(flagDuringCall).isTrue()
    assertThat(viewModel.signInInFlight.value).isFalse()
  }
}
