package dev.fanfly.wingslog.feature.attachment.model

sealed interface BlobSyncState {
  data object PendingUpload : BlobSyncState  // LOCAL_ONLY, upload_attempts == 0
  data object Uploading : BlobSyncState  // UPLOADING in-flight
  data object Synced : BlobSyncState  // SYNCED
  data object RemoteOnly : BlobSyncState  // REMOTE_ONLY, not yet downloaded
  data object Downloading :
    BlobSyncState  // download in-flight (from AttachmentOpener)

  data object UploadFailed : BlobSyncState  // LOCAL_ONLY, upload_attempts > 0
}
