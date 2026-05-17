package dev.fanfly.wingslog.feature.sync.data.blob

import dev.gitlive.firebase.storage.Data

actual fun ByteArray.toFirebaseData(): Data = Data(this)
