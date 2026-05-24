package dev.fanfly.wingslog.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.login.LoginScreen

@Composable
fun WebApp() {
    // Koin is started in main(); koinViewModel() inside LoginScreen resolves against it.
    WingslogTheme {
        var signedIn by remember { mutableStateOf(false) }
        if (signedIn) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Signed in 🎉",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        } else {
            LoginScreen(onLoginSuccess = { signedIn = true })
        }
    }
}
