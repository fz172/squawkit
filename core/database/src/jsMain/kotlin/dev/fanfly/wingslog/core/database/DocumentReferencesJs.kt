package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot

actual fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray? {
    return try {
        get<ByteArray>(field)
    } catch (e: Exception) {
        null
    }
}

actual suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean) {
    set(data, merge = merge)
}

actual fun dev.gitlive.firebase.firestore.DocumentReference.observeSnapshot(): kotlinx.coroutines.flow.Flow<dev.gitlive.firebase.firestore.DocumentSnapshot> = kotlinx.coroutines.flow.flow {
    emit(this@observeSnapshot.get())
}

actual fun dev.gitlive.firebase.firestore.Query.observeSnapshot(): kotlinx.coroutines.flow.Flow<dev.gitlive.firebase.firestore.QuerySnapshot> = kotlinx.coroutines.flow.flow {
    emit(this@observeSnapshot.get())
}
