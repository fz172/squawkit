package dev.fanfly.wingslog.core.database

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore

expect fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray?

expect suspend fun DocumentReference.setEncoded(data: Map<String, Any>, merge: Boolean = false)

fun generateRandomId(): String {
  val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
  return (1..20).map { chars.random() }.joinToString("")
}

fun FirebaseFirestore.getUserDocumentRef(firebaseAuth: FirebaseAuth): DocumentReference? {
  val userId = firebaseAuth.currentUser?.uid ?: return null
  return collection(USERS_COLLECTION).document(userId)
}

fun FirebaseFirestore.getFleetCollectionRef(firebaseAuth: FirebaseAuth): CollectionReference? =
  getUserDocumentRef(firebaseAuth)?.collection(FLEET_COLLECTION)

private const val USERS_COLLECTION = "users"
private const val FLEET_COLLECTION = "fleet"
const val AIRCRAFT_INFO_BLOB = "aircraft_info_blob"
