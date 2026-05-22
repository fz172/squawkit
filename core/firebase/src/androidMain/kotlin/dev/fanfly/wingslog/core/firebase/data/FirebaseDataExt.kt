package dev.fanfly.wingslog.core.firebase.data

import dev.gitlive.firebase.storage.Data

actual fun ByteArray.toFirebaseData(): Data = Data(this)
