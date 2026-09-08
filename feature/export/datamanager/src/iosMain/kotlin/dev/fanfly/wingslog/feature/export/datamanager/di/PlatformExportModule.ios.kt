package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformExportModule: Module = module {
  single { ExportFileStore() }
}
