package dev.fanfly.wingslog.core.database.common

import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.android

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
    // GitLive KMP SDK android extension property "android" gives the native com.google.firebase.firestore.DocumentSnapshot
    return this.android.getBlob(field)?.toBytes()
}
