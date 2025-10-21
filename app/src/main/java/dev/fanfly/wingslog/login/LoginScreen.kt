package dev.fanfly.wingslog.login

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
  googleSignInClient: GoogleSignInClient,
  onLoginSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val account = remember { GoogleSignIn.getLastSignedInAccount(context) }

  // Auto-skip login if already signed in
  LaunchedEffect(Unit) {
    if (account != null) onLoginSuccess()
  }

  var error by remember { mutableStateOf<String?>(null) }

  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    try {
      task.getResult(ApiException::class.java)
      onLoginSuccess()
    } catch (e: ApiException) {
      error = "Sign-in failed: ${e.statusCode}"
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text("Login Screen", style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(20.dp))
      Button(onClick = {
        val signInIntent: Intent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
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