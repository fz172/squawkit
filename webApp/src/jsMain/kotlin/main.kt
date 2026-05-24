import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.feature.login.di.loginModule
import dev.fanfly.wingslog.web.WebApp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Firebase JS has no google-services plugin to auto-init, so configure it
    // explicitly. Values are the project's public web-app client config.
    Firebase.initialize(
        context = null,
        options = FirebaseOptions(
            applicationId = "1:811416892017:web:6680df6dd37a69d1f961d0",
            apiKey = "AIzaSyAo52Y7aQ4jhYGq4MioZK5mSffmmZES1qk",
            projectId = "wingslog-9ca4e",
            authDomain = "wingslog-9ca4e.firebaseapp.com",
            storageBucket = "wingslog-9ca4e.firebasestorage.app",
            gcmSenderId = "811416892017",
        ),
    )

    startKoin {
        modules(commonAuthModule, authModule, loginModule)
    }

    ComposeViewport(viewportContainerId = "ComposeTarget") {
        WebApp()
    }
}
