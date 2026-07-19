package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.PostWriteHook
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.SqlDelightLocalBlobStore
import dev.fanfly.wingslog.feature.attachment.datamanager.impl.LocalFirstAttachmentManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase

/**
 * Common Koin bindings for the local-first attachment stack. [BlobFilesystem], [FileByteReader],
 * and [ImageCompressor] are provided per platform in [platformAttachmentModule].
 */
val attachmentModule = module {
  single<LocalBlobStore> {
    SqlDelightLocalBlobStore(
      db = get<WingsLogDatabase>(),
      fs = get<BlobFilesystem>(),
      ioContext = Dispatchers.Default,
      writeLock = get<DatabaseWriteLock>(),
    )
  }
  single<AttachmentManager> {
    LocalFirstAttachmentManagerImpl(
      blobs = get<LocalBlobStore>(),
      auth = get<AuthManager>(),
      fileByteReader = get<FileByteReader>(),
      imageCompressor = get<ImageCompressor>(),
      aircraftScopeResolver = get<AircraftScopeResolver>(),
      uploadScheduler = getOrNull(),
    )
  }
  single {
    QuotaChecker(
      db = get<WingsLogDatabase>(),
      ioContext = Dispatchers.Default,
    )
  }
  single<PostWriteHook> {
    BlobIndexReconciler(
      blobs = get<LocalBlobStore>(),
      coroutineScope = CoroutineScope(SupervisorJob()),
      uploadScheduler = getOrNull(),
    )
  }
}

expect val platformAttachmentModule: Module
