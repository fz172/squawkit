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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.R
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
      val credential = authManager.trySilentLogin()
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
        imageVector = Icons.Default.Flight,
        contentDescription = stringResource(
          R.string.app_name
        ), modifier = Modifier.size(64.dp)
      )
      Spacer(
        Modifier.height(20.dp)
      )
      Text(
        stringResource(
          R.string.app_name
        ), style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )
      Spacer(Modifier.height(8.dp))
      // --- Subtitle Text ---
      Text(
        text = stringResource(R.string.login_prompt),
        fontSize = 16.sp,
        color = Color.Gray
      )

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(
        thickness = 1.dp
      )
      Spacer(modifier = Modifier.height(20.dp))
      OutlinedButton(
        onClick = {
          scope.launch {
            val credential = authManager.signInWithGoogle()
            if (credential != null) {
              onLoginSuccess()
            } else {
              error = "Error signing in."
            }
          }
        }) {
        Icon(
          painter = painterResource(id = R.drawable.ic_google_rd_na),
          contentDescription = stringResource(R.string.google_logo),
          modifier = Modifier.size(24.dp),
          tint = Color.Unspecified // Use original colors
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(stringResource(R.string.sign_in_with_google))
      }
      error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}
