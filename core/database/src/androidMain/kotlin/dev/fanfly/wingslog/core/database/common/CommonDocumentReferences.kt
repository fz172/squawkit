package dev.fanfly.wingslog.core.database.common

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

fun FirebaseFirestore.getUserDocumentRef(firebaseAuth: FirebaseAuth): DocumentReference? {
  val userId = firebaseAuth.currentUser?.uid ?: return null
  return collection(USERS_COLLECTION).document(userId)
}

fun FirebaseFirestore.getFleetCollectionRef(firebaseAuth: FirebaseAuth): CollectionReference? =
  getUserDocumentRef(firebaseAuth)?.collection(FLEET_COLLECTION)

// GitLive overloads
fun dev.gitlive.firebase.firestore.FirebaseFirestore.getUserDocumentRef(
  firebaseAuth: dev.gitlive.firebase.auth.FirebaseAuth
): dev.gitlive.firebase.firestore.DocumentReference? {
  val userId = firebaseAuth.currentUser?.uid ?: return null
  return collection(USERS_COLLECTION).document(userId)
}

private const val USERS_COLLECTION = "users"


private const val FLEET_COLLECTION = "fleet"
 const val AIRCRAFT_INFO_BLOB = "aircraft_info_blob"