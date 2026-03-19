package dev.fanfly.wingslog.core.database.infra

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import org.koin.dsl.module

val firebaseModule = module {
  single { FirebaseAuth.getInstance() }
  single { FirebaseFirestore.getInstance() }

  single { Firebase.auth }        // dev.gitlive.firebase.auth.FirebaseAuth
  single { Firebase.firestore }   // dev.gitlive.firebase.firestore.FirebaseFirestore
}
