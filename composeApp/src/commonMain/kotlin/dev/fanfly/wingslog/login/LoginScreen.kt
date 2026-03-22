package dev.fanfly.wingslog.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wingslog.composeapp.generated.resources.Res
import wingslog.composeapp.generated.resources.*
import dev.fanfly.wingslog.login.data.LoginViewModel
import dev.fanfly.wingslog.platform.isAppleSignInSupported
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun LoginScreen(
  loginViewModel: LoginViewModel = koinViewModel(),
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }

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
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
      )
      Spacer(Modifier.height(8.dp))
      // --- Subtitle Text ---
      Text(
        text = stringResource(Res.string.login_prompt), fontSize = 16.sp, color = Color.Gray
      )

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(20.dp))

      // Google Sign-In button (Android only)
      if (!isAppleSignInSupported) {
        OutlinedButton(
          onClick = {
            scope.launch {
              val credential = loginViewModel.login()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = "Error signing in."
              }
            }
          }) {
          Icon(
            painter = painterResource(Res.drawable.ic_google_rd_na),
            contentDescription = stringResource(Res.string.google_logo),
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified // Use original colors
          )
          Spacer(modifier = Modifier.width(16.dp))
          Text(stringResource(Res.string.sign_in_with_google))
        }
      }

      // Apple Sign-In button (iOS only)
      if (isAppleSignInSupported) {
        OutlinedButton(
          onClick = {
            scope.launch {
              val credential = loginViewModel.loginWithApple()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = "Error signing in with Apple."
              }
            }
          }) {
          Icon(
            painter = painterResource(Res.drawable.ic_apple),
            contentDescription = stringResource(Res.string.apple_logo),
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
          )
          Spacer(modifier = Modifier.width(16.dp))
          Text(stringResource(Res.string.sign_in_with_apple))
        }
      }

      error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}
