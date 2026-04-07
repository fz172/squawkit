package dev.fanfly.wingslog.core.attachments.datamanager.impl

import dev.gitlive.firebase.storage.Data

internal expect fun ByteArray.toFirebaseData(): Data
