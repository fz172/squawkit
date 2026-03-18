package dev.fanfly.wingslog.core.database.infra

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import org.koin.dsl.module

val commonFirebaseModule = module {
  // Multiplatform (GitLive) SDK Instances
  single<FirebaseAuth> { Firebase.auth }
  single<FirebaseFirestore> { Firebase.firestore }
}
