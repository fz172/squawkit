package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.auth.EmailLinkAuthenticator
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.login.data.PendingEmailCache
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.browser.window
import kotlinx.coroutines.launch

private enum class CompletionState { Working, NeedEmail, Success, Failed }

/**
 * The lightweight page shown by the browser tab a passwordless email link opens.
 *
 * This tab can't open the OPFS database (another tab holds the single-tab lock — see [gateSingleTab]),
 * so it completes leg 2 database-free: Firebase auth persistence lives in IndexedDB, and the pending
 * email is read from [PendingEmailCache] (localStorage) rather than the database stash. Because
 * Firebase syncs auth state across tabs, the original tab receives the sign-in and advances into the
 * app on its own (see feature/login `AuthFlow`), so this tab only needs to confirm success and step
 * out of the way. Strings are hard-coded because no Koin/resource environment is started here (same
 * rationale as [ActiveElsewhereScreen]). See docs/account/email_link_signin_design.html.
 */
@Composable
internal fun EmailLinkCompletionScreen(link: String) {
  val scope = rememberCoroutineScope()
  var state by remember { mutableStateOf(CompletionState.Working) }
  var email by remember { mutableStateOf("") }

  suspend fun complete(withEmail: String): Boolean {
    val user = EmailLinkAuthenticator(Firebase.auth).completeSignInLink(withEmail, link)
    if (user == null) return false
    PendingEmailCache.clear()
    // Strip the auth params so a manual reload doesn't retry a now-consumed link.
    window.history.replaceState(null, "", window.location.pathname)
    return true
  }

  fun submit(withEmail: String) {
    scope.launch {
      state = CompletionState.Working
      state = if (complete(withEmail)) CompletionState.Success else CompletionState.Failed
    }
  }

  // Same-browser path: the sending tab stashed the address in localStorage, so complete silently.
  LaunchedEffect(Unit) {
    val stashed = PendingEmailCache.load()
    if (stashed != null) {
      state = if (complete(stashed)) CompletionState.Success else CompletionState.Failed
    } else {
      state = CompletionState.NeedEmail
    }
  }

  WingslogTheme {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        when (state) {
          CompletionState.Working -> WorkingContent()

          CompletionState.NeedEmail -> EmailPromptContent(
            email = email,
            onEmailChange = { email = it },
            onContinue = { submit(email) },
          )

          CompletionState.Success -> EmptyState(
            title = "You're signed in",
            description = "Switch back to your original SquawkIt tab to pick up where you left " +
              "off — it's already signing you in. You can safely close this tab. If SquawkIt " +
              "isn't open anywhere else, use the button below.",
            icon = Icons.Outlined.CheckCircle,
            actionText = "Open SquawkIt",
            onActionClick = { window.location.assign("/") },
            modifier = Modifier.widthIn(max = 460.dp),
          )

          CompletionState.Failed -> EmptyState(
            title = "That link didn't work",
            description = "Your sign-in link may have expired or already been used. " +
              "Head back to SquawkIt and request a new one.",
            icon = Icons.Outlined.LinkOff,
            actionText = "Back to SquawkIt",
            onActionClick = { window.location.assign("/") },
            modifier = Modifier.widthIn(max = 460.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun WorkingContent() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    CircularProgressIndicator()
    Text(
      text = "Finishing sign-in…",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun EmailPromptContent(
  email: String,
  onEmailChange: (String) -> Unit,
  onContinue: () -> Unit,
) {
  Column(
    modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "Confirm your email",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onBackground,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Enter the email address this sign-in link was sent to.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
      value = email,
      onValueChange = onEmailChange,
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { Text("you@example.com") },
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Go,
      ),
      keyboardActions = KeyboardActions(onGo = { if (email.isNotBlank()) onContinue() }),
    )
    Spacer(Modifier.height(16.dp))
    Button(
      onClick = onContinue,
      enabled = email.isNotBlank(),
      modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
      Text("Continue")
    }
  }
}
