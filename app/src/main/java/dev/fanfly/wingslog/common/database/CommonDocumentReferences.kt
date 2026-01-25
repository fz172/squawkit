package dev.fanfly.wingslog.dev.fanfly.wingslog.common.database

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

fun FirebaseFirestore.getUserDocumentRef(firebaseAuth: FirebaseAuth): DocumentReference? {
  val userId = firebaseAuth.currentUser?.uid ?: return null
  return collection(USERS_COLLECTION).document(userId)

}


private const val USERS_COLLECTION = "users"
