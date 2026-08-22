package dev.fanfly.wingslog.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import dev.fanfly.wingslog.feature.login.onboarding.AdsConsentExplainerScreen
import dev.fanfly.wingslog.feature.login.onboarding.NameEntryScreen
import dev.fanfly.wingslog.feature.login.onboarding.NotificationPrimerScreen
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import dev.fanfly.wingslog.feature.login.onboarding.OnboardingPreferences
import dev.fanfly.wingslog.feature.login.onboarding.WelcomeScreen
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.subscription.datamanager.SubscriptionManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class AuthStep { Login, EmailSignIn, NameEntry, Welcome, NotificationPrimer, AdsConsentExplainer }

/**
 * The full pre-app flow shared by every platform: sign-in → name entry → welcome → (permission
 * undetermined) notification priming → (free tier, consent required) ads consent priming.
 *
 * Navigation-free (a simple step state machine) so it runs identically on Android, iOS, and web
 * without depending on a navigation library. [onComplete] fires once the user is signed in and has
 * finished (or already finished) onboarding — the host decides where to go next (the fleet on
 * mobile, a placeholder on web today).
 *
 * Name persistence is delegated to [OnboardingActions]; the welcome flag goes
 * through [OnboardingPreferences] (local store). Neither the notification primer nor the ads
 * consent step has a flag of its own — both run every time onboarding completes (new signup or
 * returning user alike) and rely on the platform's own cached state (OS permission state; the CMP's)
 * to no-op once already resolved.
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
  subscriptionManager: SubscriptionManager = koinInject(),
  adConsentManager: AdConsentManager = koinInject(),
  notificationPermission: NotificationPermission = koinInject(),
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

  // The primer's Continue must re-enter here, below the notification check, rather than back through
  // proceedPastOnboarding() — that would re-read a permission state the OS may not have committed yet
  // and show the primer again. This is the one place ads consent gets resolved, instead of leaving it
  // to whichever ad slot happens to render first. isConsentRequired() is a background check (no UI);
  // only when it says a privacy choice is actually needed does the flow detour through the explainer
  // + the real CMP dialog, rather than interrupt a pilot mid-scroll later. showsAds() already folds
  // in both the tier check and isAdsSupported, so a Pro user's app never even calls the consent SDK.
  suspend fun proceedPastNotifications() {
    val needsAdsConsent = subscriptionManager.shouldShowAds()
      .first() && adConsentManager.isConsentRequired()
    if (needsAdsConsent) {
      step = AuthStep.AdsConsentExplainer
    } else {
      onComplete()
    }
  }

  // Every path out of onboarding funnels through here, new signup and returning user alike — this
  // is the one place the notification permission gets resolved, instead of leaving the first ask to
  // whichever screen happens to touch NotificationPermission first. refresh() before the check:
  // observe()'s StateFlow can be stale if the user changed the OS setting since the app last looked,
  // and this is the one read that decides whether the primer appears at all. The equality check
  // against UNDETERMINED (not e.g. "!= GRANTED && != DENIED") is deliberate — it is what keeps
  // UNSUPPORTED out of the primer, since a device that cannot show notifications has nothing to
  // prime.
  suspend fun proceedPastOnboarding() {
    notificationPermission.refresh()
    if (notificationPermission.observe().value == PermissionState.UNDETERMINED) {
      step = AuthStep.NotificationPrimer
      return
    }
    proceedPastNotifications()
  }

  // Guards against advancing past sign-in twice — a real risk once we also advance from
  // authStateChanged (below), which can race the manual onLoginSuccess call. Reset whenever we
  // return to the Login step so a user who backs out can sign in again.
  var advanced by remember { mutableStateOf(false) }
  LaunchedEffect(step) { if (step == AuthStep.Login) advanced = false }

  // A returning user who already finished onboarding skips straight through; otherwise route to
  // name entry / welcome. Shared by every sign-in path (Google/Apple/email/anonymous).
  val onLoginSuccess: () -> Unit = {
    if (!advanced) {
      advanced = true
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
          proceedPastOnboarding()
        }
      }
    }
  }

  // Advance when Firebase reports a sign-in that happened after we showed the login flow — including
  // a sign-in completed in ANOTHER tab. On web an email link opens in a separate tab that completes
  // leg 2 there (see webApp EmailLinkCompletionScreen); Firebase syncs the auth state across tabs, so
  // this tab receives the user here and moves into onboarding/app. Only a null -> user transition
  // triggers it, so a returning user resolved at startup still flows through silentLogin unchanged.
  //
  // Firebase reports the user the instant a credential is accepted, which can be *before* the
  // provider call that started it has finished writing the profile — Sign in with Apple supplies
  // the display name outside the credential, so it lands a round trip later. Reading displayName at
  // that point would route a named user to name entry. So wait for any local sign-in to settle
  // first; the `advanced` guard then makes this a no-op whenever the caller already advanced us.
  LaunchedEffect(Unit) {
    var sawSignedOut = false
    firebaseAuth.authStateChanged.collect { user ->
      if (user == null) {
        sawSignedOut = true
      } else if (sawSignedOut) {
        loginViewModel.signInInFlight.first { !it }
        onLoginSuccess()
      }
    }
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
          proceedPastOnboarding()
        }
      },
    )

    AuthStep.NotificationPrimer -> NotificationPrimerScreen(
      onContinue = {
        scope.launch {
          notificationPermission.request() // the real OS dialog; its result is not branched on
          proceedPastNotifications()
        }
      },
    )

    AuthStep.AdsConsentExplainer -> AdsConsentExplainerScreen(
      onContinue = {
        scope.launch {
          adConsentManager.presentConsentForm()
          onComplete()
        }
      },
    )
  }
}
