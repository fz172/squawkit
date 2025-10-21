package dev.fanfly.wingslog.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.login.data.AuthManager
import kotlinx.coroutines.launch


@Composable
fun LoginScreen(
  authManager: AuthManager,
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }

  // Try silent sign-in first
  LaunchedEffect(Unit) {
    scope.launch {
      val credential = authManager.login()
      if (credential != null) {
        onLoginSuccess()
      }
    }
  }

  // Manual sign-in button
  Surface(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text("Login Screen", style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(20.dp))
      Button(onClick = {
        scope.launch {
          val credential = authManager.login()
          if (credential != null) {
            onLoginSuccess()
          } else {
            error = "Error signing in."
          }
        }
      }) {
        Text("Sign in with Google")
      }
      error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}
