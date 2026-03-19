package dev.fanfly.wingslog.core.database.infra

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.koin.dsl.module

val firebaseModule = module {
  single { FirebaseAuth.getInstance() }
  single { FirebaseFirestore.getInstance() }
}
