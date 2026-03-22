package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Query
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
    // Return null or stub for now. Ideally should use native iOS Firestore APIs.
    return null
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
    // Stub for now. Ideally should use native iOS Firestore APIs.
}

actual fun DocumentReference.observeSnapshot(): Flow<DocumentSnapshot> {
    return this.snapshots
}

actual fun Query.observeSnapshot(): Flow<QuerySnapshot> {
    return this.snapshots
}
