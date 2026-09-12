package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportNotifications
import dev.fanfly.wingslog.feature.export.datamanager.impl.WorkManagerExportJobCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformExportModule: Module = module {
  single {
    ExportRunPolicy(stopWhenBackgrounded = false, survivesLeavingScreen = true)
  }
  single<ExportJobCoordinator> { WorkManagerExportJobCoordinator(androidContext()) }
  single { ExportFileStore(androidContext()) }
  single { ExportNotifications(androidContext()) }
}
