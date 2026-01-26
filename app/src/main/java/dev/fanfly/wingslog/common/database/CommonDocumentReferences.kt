package dev.fanfly.wingslog.common.database

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

private const val USERS_COLLECTION = "users"


private const val FLEET_COLLECTION = "fleet"
 const val AIRCRAFT_INFO_BLOB = "aircraft_info_blob"