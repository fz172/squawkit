package dev.fanfly.wingslog.feature.sync.data.blob

import dev.gitlive.firebase.storage.Data

expect fun ByteArray.toFirebaseData(): Data
