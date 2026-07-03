import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fanfly.wingslog.core.appinfo.BuildInfo
import dev.fanfly.wingslog.core.di.commonAppModules
import dev.fanfly.wingslog.feature.stresstest.config.stressTestKoinModules
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.web.ActiveElsewhereScreen
import dev.fanfly.wingslog.web.EmailLinkCompletionScreen
import dev.fanfly.wingslog.web.EmojiFallbackProvider
import dev.fanfly.wingslog.web.WebApp
import dev.fanfly.wingslog.web.createSqliteWorker
import dev.fanfly.wingslog.web.gateSingleTab
import dev.fanfly.wingslog.web.initializeApp
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.w3c.dom.Worker
import kotlin.js.json

// Resolved from the webpack-injected __WINGSLOG_DEBUG__ constant; falls back to false (release)
// when the define is absent, e.g. the plain dev server.
private val isWebDebugBuild: Boolean =
  js("(typeof __WINGSLOG_DEBUG__ !== 'undefined') ? __WINGSLOG_DEBUG__ : false") as Boolean

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  // Firebase must be initialized before anything (below) touches auth. Done once here so every
  // startup path — the email-link completion tab and both single-tab-gate branches — shares the
  // same default app.
  initializeFirebase()

  // A passwordless email link opens in a NEW tab while the original tab still holds the OPFS
  // database lock, so this tab can't open the database. Complete leg 2 here without it: Firebase
  // auth lives in IndexedDB (not OPFS) and syncs across tabs, so the original tab picks up the
  // sign-in and advances. See EmailLinkCompletionScreen. This must run before the single-tab gate,
  // which would otherwise strand this tab on ActiveElsewhereScreen.
  val href = window.location.href
  if (Firebase.auth.isSignInWithEmailLink(href)) {
    ComposeViewport(viewportContainerId = "ComposeTarget") {
      EmailLinkCompletionScreen(link = href)
    }
    return
  }

  // The local database lives in OPFS, which only one tab can open at a time. Gate startup on an
  // exclusive Web Lock: the first tab runs the app and holds the lock for its lifetime; any other
  // tab shows ActiveElsewhereScreen instead of crashing the SQLite worker on createSyncAccessHandle.
  gateSingleTab(
    onPrimary = ::startPrimaryTab,
    onActiveElsewhere = {
      ComposeViewport(viewportContainerId = "ComposeTarget") {
        ActiveElsewhereScreen(onReload = { window.location.reload() })
      }
    },
  )
}

// Firebase JS has no google-services plugin to auto-init, so configure the default app explicitly.
// We call initializeApp directly (not GitLive's Firebase.initialize) so the config can carry
// measurementId — required for Firebase Analytics, but not exposed by GitLive's FirebaseOptions.
// GitLive's auth / firestore / storage resolve this same default app. Values are the project's
// public web-app client config.
private fun initializeFirebase() {
  initializeApp(
    json(
      "apiKey" to "AIzaSyAo52Y7aQ4jhYGq4MioZK5mSffmmZES1qk",
      // Custom auth domain so the Google sign-in chooser reads "continue to squawkit.fanfly.dev"
      // instead of the default *.firebaseapp.com. The custom domain serves the /__/auth/handler
      // endpoint via Firebase Hosting; it must be an Authorized domain (Firebase Auth) and an
      // authorized redirect URI on the OAuth web client.
      "authDomain" to "squawkit.fanfly.dev",
      "projectId" to "wingslog-9ca4e",
      "storageBucket" to "wingslog-9ca4e.firebasestorage.app",
      "messagingSenderId" to "811416892017",
      "appId" to "1:811416892017:web:6680df6dd37a69d1f961d0",
      "measurementId" to "G-VPNQ92VG8F",
    ),
  )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun startPrimaryTab() {
  val koinApplication = startKoin {
    modules(
      commonAppModules + stressTestKoinModules() + listOf(
        module {
          // The host app owns the bundled sqlite-wasm worker file (persists to OPFS).
          single<Worker> { createSqliteWorker() }
          // __WINGSLOG_DEBUG__ is injected at bundle time by webpack DefinePlugin
          // (webpack.config.d/debug-flag.js); true only for the debug web build, which surfaces
          // developer-only entries like Feature Lab. Guarded with typeof so it's safe if undefined.
          single { BuildInfo(isDeveloperBuild = isWebDebugBuild) }
        },
      ),
    )
  }
  koinApplication.koin.get<SyncEngine>()
    .start()

  ComposeViewport(viewportContainerId = "ComposeTarget") {
    EmojiFallbackProvider {
      WebApp()
    }
  }
}
