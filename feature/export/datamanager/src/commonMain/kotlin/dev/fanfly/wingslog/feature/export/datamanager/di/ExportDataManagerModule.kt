package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobFilesystem
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.AttachmentExportResolver
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportDeliveryBackend
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportHistoryRemoteRepository
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportAggregator
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportArchiveBuilder
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.ZipFileWriter
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import org.koin.dsl.module

val exportDataManagerModule = module {
  single { ZipFileWriter() }
  single { LogbookExportArchiveBuilder() }
  single {
    AttachmentExportResolver(
      attachmentManager = get<AttachmentManager>(),
      localBlobStore = get<LocalBlobStore>(),
      blobFilesystem = get<BlobFilesystem>(),
    )
  }
  single {
    LogbookExportAggregator(
      fleetManager = get<FleetManager>(),
      logsManager = get<MaintenanceLogManager>(),
      tasksManager = get<TaskDataManager>(),
      taskDueManager = get<TaskDueManager>(),
      squawkManager = get<SquawkManager>(),
      technicianManager = get<TechnicianManager>(),
    )
  }
  single {
    ExportHistoryRemoteRepository(
      auth = get(),
      firestore = get(),
      storage = get(),
    )
  }
  single { ExportDeliveryBackend() }
  single<ExportManager> {
    LogbookExportManager(
      aggregator = get<LogbookExportAggregator>(),
      attachmentExportResolver = get<AttachmentExportResolver>(),
      archiveBuilder = get<LogbookExportArchiveBuilder>(),
      zipFileWriter = get<ZipFileWriter>(),
      exportFileStore = get<ExportFileStore>(),
      remoteRepository = get<ExportHistoryRemoteRepository>(),
      deliveryBackend = get<ExportDeliveryBackend>(),
    )
  }
}
