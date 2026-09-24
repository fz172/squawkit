package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.feature.login.chrome.LoginErrorStyle

@Composable
internal fun ErrorLine(message: String) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = message,
      color = MaterialTheme.colorScheme.error,
      style = LoginErrorStyle,
      textAlign = TextAlign.Start,
      modifier = Modifier.align(Alignment.CenterStart),
    )
  }
}
