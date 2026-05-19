package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.StubExportManager
import org.koin.dsl.module

val exportDataManagerModule = module {
  single<ExportManager> { StubExportManager() }
}
