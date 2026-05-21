package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.gitlive.firebase.storage.Data

actual fun ByteArray.toFirebaseData(): Data = Data(this)
