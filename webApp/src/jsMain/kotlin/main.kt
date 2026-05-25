import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.core.storage.di.platformStorageModule
import dev.fanfly.wingslog.core.storage.di.storageModule
import dev.fanfly.wingslog.feature.login.di.loginModule
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.di.syncModule
import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.web.WebApp
import dev.fanfly.wingslog.web.createSqlJsWorker
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.w3c.dom.Worker

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

    val koinApplication = startKoin {
        modules(
            commonAuthModule,
            authModule,
            storageModule,
            platformStorageModule,
            loginModule,
            syncModule,
            technicianDataManagerModule,
            module {
                // The host app owns the bundled sql.js worker file (persists to IndexedDB).
                single<Worker> { createSqlJsWorker() }
            },
        )
    }
    koinApplication.koin.get<SyncEngine>().start()

    ComposeViewport(viewportContainerId = "ComposeTarget") {
        WebApp()
    }
}
