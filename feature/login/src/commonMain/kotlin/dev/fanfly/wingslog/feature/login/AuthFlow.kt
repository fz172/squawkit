package dev.fanfly.wingslog.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class AuthStep { Login, EmailSignIn, NameEntry, Welcome }

/**
 * The full pre-app flow shared by every platform: sign-in → name entry → welcome.
 *
 * Navigation-free (a simple step state machine) so it runs identically on Android, iOS, and web
 * without depending on a navigation library. [onComplete] fires once the user is signed in and has
 * finished (or already finished) onboarding — the host decides where to go next (the fleet on
 * mobile, a placeholder on web today).
 *
 * Name persistence is delegated to [OnboardingActions]; the welcome flag goes
 * through [OnboardingPreferences] (local store).
 *
 * [loginContent] is the sign-in step's UI. It defaults to the shared [LoginScreen] used by Android
 * and iOS; the web host overrides it with its SEO landing page (see WebLoginLandingScreen) while
 * reusing the same onboarding tail. The slot receives `onLoginSuccess` (invoke once the user is
 * authenticated) and `onChooseEmail` (navigate to the shared [EmailSignInScreen]).
 */
@Composable
fun AuthFlow(
  onComplete: () -> Unit,
  loginViewModel: LoginViewModel = koinViewModel(),
  firebaseAuth: FirebaseAuth = koinInject(),
  actions: OnboardingActions = koinInject(),
  onboardingPreferences: OnboardingPreferences = koinInject(),
  loginContent: @Composable (onLoginSuccess: () -> Unit, onChooseEmail: () -> Unit) -> Unit =
    { onLoginSuccess, onChooseEmail ->
      LoginScreen(
        loginViewModel = loginViewModel,
        onLoginSuccess = onLoginSuccess,
        onChooseEmail = onChooseEmail,
      )
    },
) {
  val scope = rememberCoroutineScope()
  var step by remember { mutableStateOf(AuthStep.Login) }
  val selfName by actions.observeSelfName()
    .collectAsState(null)

  // A returning user who already finished onboarding skips straight through; otherwise route to
  // name entry / welcome. Shared by every sign-in path (Google/Apple/email/anonymous).
  val onLoginSuccess: () -> Unit = {
    scope.launch {
      val accountName = firebaseAuth.currentUser?.displayName.orEmpty()
      val localSelfName = actions.observeSelfName()
        .firstOrNull()
        .orEmpty()
      if (accountName.isBlank() && localSelfName.isBlank()) {
        step = AuthStep.NameEntry
      } else if (!onboardingPreferences.checkHasSeenWelcome()) {
        step = AuthStep.Welcome
      } else {
        onComplete()
      }
    }
    Unit
  }

  // An inbound email sign-in link (deep link / fresh web load) jumps straight to the email page,
  // which completes leg 2. Works even if the user never tapped the email button on this device.
  val pendingLink by EmailLinkDeepLinks.pendingLink.collectAsState()
  LaunchedEffect(pendingLink) {
    val link = pendingLink ?: return@LaunchedEffect
    if (step == AuthStep.Login && loginViewModel.isEmailSignInLink(link)) {
      step = AuthStep.EmailSignIn
    }
  }

  when (step) {
    AuthStep.Login -> loginContent(
      onLoginSuccess,
      { step = AuthStep.EmailSignIn },
    )

    AuthStep.EmailSignIn -> EmailSignInScreen(
      loginViewModel = loginViewModel,
      onBack = { step = AuthStep.Login },
      onLoginSuccess = onLoginSuccess,
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
