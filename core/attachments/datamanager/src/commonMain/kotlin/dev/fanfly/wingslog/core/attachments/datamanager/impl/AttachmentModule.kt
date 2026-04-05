package dev.fanfly.wingslog.core.attachments.datamanager.impl

import dev.fanfly.wingslog.core.attachments.datamanager.AttachmentManager
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.storage.storage
import org.koin.core.module.Module
import org.koin.dsl.module

/** Common Koin bindings. Platform-specific bindings (FileByteReader, AttachmentOpener) live in [platformAttachmentModule]. */
val attachmentModule = module {
  single<AttachmentManager> {
    AttachmentManagerImpl(
      storage = Firebase.storage,
      auth = Firebase.auth,
      fileByteReader = get(),
    )
  }
}

/** Platform-specific bindings: FileByteReader, AttachmentOpener. */
expect val platformAttachmentModule: Module
