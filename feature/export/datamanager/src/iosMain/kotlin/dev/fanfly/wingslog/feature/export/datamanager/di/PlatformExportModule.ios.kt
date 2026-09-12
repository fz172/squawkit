package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.ExportJobCoordinator
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import dev.fanfly.wingslog.feature.export.datamanager.impl.InProcessExportJobCoordinator
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformExportModule: Module = module {
  single {
    ExportRunPolicy(stopWhenBackgrounded = true, survivesLeavingScreen = false)
  }
  single<ExportJobCoordinator> { InProcessExportJobCoordinator(get<ExportManager>()) }
  single { ExportFileStore() }
}
