package dev.fanfly.wingslog.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import dev.fanfly.wingslog.feature.login.onboarding.NameEntryScreen
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingPreferences
import dev.fanfly.wingslog.feature.login.onboarding.WelcomeScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class AuthStep { Login, NameEntry, Welcome }

/**
 * The full pre-app flow shared by every platform: sign-in → name entry → welcome.
 *
 * Navigation-free (a simple step state machine) so it runs identically on Android, iOS, and web
 * without depending on a navigation library. [onComplete] fires once the user is signed in and has
 * finished (or already finished) onboarding — the host decides where to go next (the fleet on
 * mobile, a placeholder on web today).
 *
 * Name persistence is delegated to [OnboardingActions] (app-provided); the welcome flag goes
 * through [OnboardingPreferences] (local store).
 */
@Composable
fun AuthFlow(
  onComplete: () -> Unit,
  loginViewModel: LoginViewModel = koinViewModel(),
  actions: OnboardingActions = koinInject(),
  onboardingPreferences: OnboardingPreferences = koinInject(),
) {
  val scope = rememberCoroutineScope()
  var step by remember { mutableStateOf(AuthStep.Login) }
  val selfName by actions.observeSelfName().collectAsState(null)

  when (step) {
    AuthStep.Login -> LoginScreen(
      loginViewModel = loginViewModel,
      onLoginSuccess = {
        // A returning user who already finished onboarding skips straight through.
        scope.launch {
          if (onboardingPreferences.checkHasSeenWelcome()) onComplete()
          else step = AuthStep.NameEntry
        }
      },
    )

    AuthStep.NameEntry -> NameEntryScreen(
      initialName = selfName.orEmpty(),
      onBack = { step = AuthStep.Login },
      onNext = { name ->
        scope.launch {
          actions.saveSelfName(name)
          step = AuthStep.Welcome
        }
      },
    )

    AuthStep.Welcome -> WelcomeScreen(
      name = selfName.orEmpty(),
      onDone = {
        scope.launch {
          onboardingPreferences.setHasSeenWelcome()
          onComplete()
        }
      },
    )
  }
}
