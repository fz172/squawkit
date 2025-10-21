package dev.fanfly.wingslog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.fanfly.wingslog.login.data.AuthManager

class MainActivity : ComponentActivity() {

  private val authManager = AuthManager(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppEntry(authManager)
    }
  }
}