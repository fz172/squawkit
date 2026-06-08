import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
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
import dev.fanfly.wingslog.web.EmojiFallbackProvider
import dev.fanfly.wingslog.web.WebApp
import dev.fanfly.wingslog.web.createSqliteWorker
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
