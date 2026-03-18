package dev.fanfly.wingslog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WingslogTheme {
        Surface(
          modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) { AppEntry() }

      }
    }
  }
}