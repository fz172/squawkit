package dev.fanfly.wingslog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dev.fanfly.wingslog.ui.theme.WingslogTheme

class MainActivity : ComponentActivity() {
  private lateinit var googleSignInClient: GoogleSignInClient


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val gso =
      GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
    googleSignInClient = GoogleSignIn.getClient(this, gso)

    enableEdgeToEdge()
    setContent {
      AppEntry(googleSignInClient)
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(
    text = "Hello $name!", modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  WingslogTheme {
    Greeting("Android")
  }
}