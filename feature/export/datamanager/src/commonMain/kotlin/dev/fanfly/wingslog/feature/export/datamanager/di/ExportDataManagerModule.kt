package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.AttachmentExportResolver
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportAggregator
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportArchiveBuilder
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.ZipFileWriter
import org.koin.dsl.module

val exportDataManagerModule = module {
  single { ExportFileStore() }
  single { ZipFileWriter() }
  single { LogbookExportArchiveBuilder() }
  single {
    AttachmentExportResolver(
      attachmentManager = get(),
      localBlobStore = get(),
      blobFilesystem = get(),
    )
  }
  single {
    LogbookExportAggregator(
      fleetManager = get(),
      logsManager = get(),
      tasksManager = get(),
      taskDueManager = get(),
      squawkManager = get(),
      technicianManager = get(),
    )
  }
  single<ExportManager> {
    LogbookExportManager(
      aggregator = get(),
      attachmentExportResolver = get(),
      archiveBuilder = get(),
      zipFileWriter = get(),
      exportFileStore = get(),
    )
  }
}
