package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.ExportRunPolicy
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformExportModule: Module = module {
  single { ExportRunPolicy(stopWhenBackgrounded = false) }
  single { ExportFileStore(androidContext()) }
}
