package dev.fanfly.wingslog.feature.export.datamanager.di

import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentManager
import dev.fanfly.wingslog.feature.export.datamanager.ExportManager
import dev.fanfly.wingslog.feature.export.datamanager.impl.AttachmentExportResolver
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportDeliveryBackend
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportFileStore
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportHistoryRemoteRepository
import dev.fanfly.wingslog.feature.export.datamanager.impl.ExportManagerImpl
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportAggregator
import dev.fanfly.wingslog.feature.export.datamanager.impl.LogbookExportArchiveBuilder
import dev.fanfly.wingslog.feature.export.datamanager.impl.ZipFileWriter
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDataManager
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.storage.FirebaseStorage
import io.ktor.client.HttpClient
import org.koin.dsl.module

val exportDataManagerModule = module {
  single { ZipFileWriter() }
  single { LogbookExportArchiveBuilder(templateRegistry = get<TemplateRegistry>()) }
  single {
    AttachmentExportResolver(
      get<AttachmentManager>(),
      get<LocalBlobStore>(),
      get<BlobFilesystem>(),
    )
  }
  single {
    LogbookExportAggregator(
      get<FleetManager>(),
      get<MaintenanceLogManager>(),
      get<TaskDataManager>(),
      get<TaskDueManager>(),
      get<SquawkManager>(),
      get<TechnicianManager>(),
    )
  }
  single {
    ExportHistoryRemoteRepository(
      get<FirebaseAuth>(),
      get<FirebaseFirestore>(),
      get<FirebaseStorage>(),
      get<HttpClient>(),
    )
  }
  single { ExportDeliveryBackend(get<FirebaseFunctions>()) }
  single<ExportManager> {
    ExportManagerImpl(
      aggregator = get<LogbookExportAggregator>(),
      attachmentExportResolver = get<AttachmentExportResolver>(),
      archiveBuilder = get<LogbookExportArchiveBuilder>(),
      templateRegistry = get<TemplateRegistry>(),
      zipFileWriter = get<ZipFileWriter>(),
      exportFileStore = get<ExportFileStore>(),
      remoteRepository = get<ExportHistoryRemoteRepository>(),
      deliveryBackend = get<ExportDeliveryBackend>(),
      auth = get<FirebaseAuth>(),
    )
  }
}
