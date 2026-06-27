import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.fanfly.wingslog.core.appinfo.BuildInfo
import dev.fanfly.wingslog.core.analytics.di.platformAnalyticsModule
import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.auth.di.commonAuthModule
import dev.fanfly.wingslog.core.storage.di.platformStorageModule
import dev.fanfly.wingslog.core.storage.di.storageModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceModule
import dev.fanfly.wingslog.core.ui.theme.di.appearanceStoreModule
import dev.fanfly.wingslog.feature.aircraft.dashboard.di.aircraftDashboardModule
import dev.fanfly.wingslog.feature.attachment.datamanager.attachmentModule
import dev.fanfly.wingslog.feature.attachment.datamanager.platformAttachmentModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportDataManagerModule
import dev.fanfly.wingslog.feature.export.datamanager.di.exportPlatformModule
import dev.fanfly.wingslog.feature.export.update.viewmodel.exportUiModule
import dev.fanfly.wingslog.feature.featurelab.datamanager.di.featureLabModule
import dev.fanfly.wingslog.feature.fleet.datamanager.di.fleetDataManagerModule
import dev.fanfly.wingslog.feature.fleet.viewing.di.fleetViewingModule
import dev.fanfly.wingslog.feature.login.di.loginModule
import dev.fanfly.wingslog.feature.logs.datamanager.impl.maintenanceDataManagerModule
import dev.fanfly.wingslog.feature.logs.update.di.maintenanceUpdateModule
import dev.fanfly.wingslog.feature.logs.viewing.di.maintenanceViewingModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.squawk.datamanager.squawkModule
import dev.fanfly.wingslog.feature.squawk.update.viewmodel.squawkUiModule
import dev.fanfly.wingslog.feature.stresstest.config.stressTestKoinModules
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.blob.di.blobSchedulerModule
import dev.fanfly.wingslog.feature.sync.data.di.syncModule
import dev.fanfly.wingslog.feature.sync.settings.di.syncSettingsModule
import dev.fanfly.wingslog.feature.tasks.datamanager.tasksModule
import dev.fanfly.wingslog.feature.tasks.update.viewmodel.tasksUiModule
import dev.fanfly.wingslog.feature.technician.datamanager.di.technicianDataManagerModule
import dev.fanfly.wingslog.feature.technician.manage.di.technicianManageModule
import dev.fanfly.wingslog.web.ActiveElsewhereScreen
import dev.fanfly.wingslog.web.EmojiFallbackProvider
import dev.fanfly.wingslog.web.WebApp
import dev.fanfly.wingslog.web.createSqliteWorker
import dev.fanfly.wingslog.web.gateSingleTab
import dev.fanfly.wingslog.web.initializeApp
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

@OptIn(ExperimentalComposeUiApi::class)
private fun startPrimaryTab() {
  // Firebase JS has no google-services plugin to auto-init, so configure the default app
  // explicitly. We call initializeApp directly (not GitLive's Firebase.initialize) so the config
  // can carry measurementId — required for Firebase Analytics, but not exposed by GitLive's
  // FirebaseOptions. GitLive's auth / firestore / storage resolve this same default app.
  // Values are the project's public web-app client config.
  initializeApp(
    json(
      "apiKey" to "AIzaSyAo52Y7aQ4jhYGq4MioZK5mSffmmZES1qk",
      "authDomain" to "wingslog-9ca4e.firebaseapp.com",
      "projectId" to "wingslog-9ca4e",
      "storageBucket" to "wingslog-9ca4e.firebasestorage.app",
      "messagingSenderId" to "811416892017",
      "appId" to "1:811416892017:web:6680df6dd37a69d1f961d0",
      "measurementId" to "G-VPNQ92VG8F",
    ),
  )

  val koinApplication = startKoin {
    modules(
      platformAnalyticsModule,
      commonAuthModule,
      authModule,
      storageModule,
      platformStorageModule,
      appearanceModule,
      appearanceStoreModule,
      loginModule,
      syncModule,
      blobSchedulerModule,
      attachmentModule,
      platformAttachmentModule,
      featureLabModule,
      technicianDataManagerModule,
      fleetDataManagerModule,
      fleetViewingModule,
      maintenanceDataManagerModule,
      maintenanceViewingModule,
      maintenanceUpdateModule,
      tasksModule,
      tasksUiModule,
      squawkModule,
      squawkUiModule,
      aircraftDashboardModule,
      technicianManageModule,
      exportDataManagerModule,
      exportPlatformModule,
      exportUiModule,
      settingsModule,
      syncSettingsModule,
      *stressTestKoinModules().toTypedArray(),
      module {
        // The host app owns the bundled sqlite-wasm worker file (persists to OPFS).
        single<Worker> { createSqliteWorker() }
        // __WINGSLOG_DEBUG__ is injected at bundle time by webpack DefinePlugin
        // (webpack.config.d/debug-flag.js); true only for the debug web build, which surfaces
        // developer-only entries like Feature Lab. Guarded with typeof so it's safe if undefined.
        single { BuildInfo(isDeveloperBuild = isWebDebugBuild) }
      },
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
