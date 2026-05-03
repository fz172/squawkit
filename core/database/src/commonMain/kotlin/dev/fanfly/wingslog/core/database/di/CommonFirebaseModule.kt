package dev.fanfly.wingslog.core.database.di

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import org.koin.dsl.module

val commonFirebaseModule = module {
  // Multiplatform (GitLive) SDK Instances
  single<FirebaseAuth> { Firebase.auth }
  single<FirebaseFirestore> {
    // R1 local-first: the SQLDelight `entity` table is the source of truth, and `core/sync`
    // owns the dirty-row queue. Firestore's own offline cache would be duplicate state — disable
    // it (memory cache only) so debugging shows one queue, not two. Settings must be applied
    // before any Firestore op, which is why this lives in the Koin factory.
    Firebase.firestore.apply {
      settings = firestoreSettings { cacheSettings = memoryCacheSettings {} }
    }
  }
}
