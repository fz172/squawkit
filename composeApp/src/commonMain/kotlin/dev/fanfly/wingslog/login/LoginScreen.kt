package dev.fanfly.wingslog.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.composeapp.generated.resources.Res
import wingslog.composeapp.generated.resources.app_name
import wingslog.composeapp.generated.resources.continue_without_account
import wingslog.composeapp.generated.resources.google_logo
import wingslog.composeapp.generated.resources.ic_google_rd_na
import wingslog.composeapp.generated.resources.ic_launcher_foreground
import wingslog.composeapp.generated.resources.login_prompt
import wingslog.composeapp.generated.resources.sign_in_anonymous_error
import wingslog.composeapp.generated.resources.sign_in_error
import wingslog.composeapp.generated.resources.sign_in_with_google


@Composable
fun LoginScreen(
  loginViewModel: LoginViewModel = koinViewModel(),
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  var isSigningIn by remember { mutableStateOf(false) }
  val signInErrorMessage = stringResource(Res.string.sign_in_error)
  val signInAnonymousErrorMessage = stringResource(Res.string.sign_in_anonymous_error)

  // Try silent sign-in first
  LaunchedEffect(Unit) {
    scope.launch {
      val credential = loginViewModel.silentLogin()
      if (credential != null) {
        onLoginSuccess()
      }
    }
  }

  // Manual sign-in button
  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        painter = painterResource(Res.drawable.ic_launcher_foreground),
        contentDescription = stringResource(Res.string.app_name),
        modifier = Modifier.size(256.dp),
        tint = Color.Unspecified
      )

      Text(
        stringResource(Res.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(Modifier.height(8.dp))
      // --- Subtitle Text ---
      Text(
        text = stringResource(Res.string.login_prompt),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(20.dp))
      OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningIn,
        onClick = {
          scope.launch {
            isSigningIn = true
            try {
              val credential = loginViewModel.login()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInErrorMessage
              }
            } finally {
              isSigningIn = false
            }
          }
        }) {
        if (isSigningIn) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
          Icon(
            painter = painterResource(Res.drawable.ic_google_rd_na),
            contentDescription = stringResource(Res.string.google_logo),
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(stringResource(Res.string.sign_in_with_google))
      }

      // Anonymous / Guest sign-in button (all platforms)
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSigningIn,
        onClick = {
          scope.launch {
            isSigningIn = true
            try {
              val credential = loginViewModel.loginAnonymously()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInAnonymousErrorMessage
              }
            } finally {
              isSigningIn = false
            }
          }
        }) {
        Icon(
          imageVector = Icons.Filled.Person,
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(stringResource(Res.string.continue_without_account))
      }

      error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}
