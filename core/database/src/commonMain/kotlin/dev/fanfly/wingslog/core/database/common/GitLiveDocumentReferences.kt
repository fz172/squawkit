package dev.fanfly.wingslog.core.database.common

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.DocumentSnapshot

expect fun DocumentSnapshot.getBlobAsBytes(field: String): ByteArray?

fun FirebaseFirestore.getGitLiveUserDocumentRef(firebaseAuth: FirebaseAuth): DocumentReference? {
  val userId = firebaseAuth.currentUser?.uid ?: return null
  return collection(GITLIVE_USERS_COLLECTION).document(userId)
}

fun FirebaseFirestore.getGitLiveFleetCollectionRef(firebaseAuth: FirebaseAuth): CollectionReference? =
  getGitLiveUserDocumentRef(firebaseAuth)?.collection(GITLIVE_FLEET_COLLECTION)

private const val GITLIVE_USERS_COLLECTION = "users"
private const val GITLIVE_FLEET_COLLECTION = "fleet"
const val GITLIVE_AIRCRAFT_INFO_BLOB = "aircraft_info_blob"
