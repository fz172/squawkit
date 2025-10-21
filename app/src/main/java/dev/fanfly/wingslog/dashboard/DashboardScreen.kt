package dev.fanfly.wingslog.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DashboardScreen() {
  Surface(
    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
  ) {
    Box(
      modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
      Text("Hello World – You’re Logged In!", style = MaterialTheme.typography.headlineMedium)
    }
  }
}