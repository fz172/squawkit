package dev.fanfly.wingslog.feature.sync.data.di

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.data.FirestorePullSubscription
import dev.fanfly.wingslog.feature.sync.data.FirestoreRemoteFetcher
import dev.fanfly.wingslog.feature.sync.data.FirestoreSyncWriter
import dev.fanfly.wingslog.feature.sync.data.HydrationRunner
import dev.fanfly.wingslog.feature.sync.data.PullListener
import dev.fanfly.wingslog.feature.sync.data.PushWorker
import dev.fanfly.wingslog.feature.sync.data.RemoteFetcher
import dev.fanfly.wingslog.feature.sync.data.SyncCursorStore
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.SyncWriter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the [SyncEngine] and its collaborators. Provides the [FirebaseFirestore] singleton itself,
 * depends on `storageModule` for the database + [EntityStoreFactory], and on `commonAuthModule`
 * for [FirebaseAuth].
 */
val syncModule: Module = module {

  single<FirebaseFirestore> {
    // R1 local-first: the SQLDelight `entity` table is the source of truth, and `core/sync`
    // owns the dirty-row queue. Firestore's own offline cache would be duplicate state — disable
    // it (memory cache only) so debugging shows one queue, not two. Settings must be applied
    // before any Firestore op, which is why this lives in the Koin factory.
    Firebase.firestore.apply {
      settings = firestoreSettings { cacheSettings = memoryCacheSettings {} }
    }
  }
  single<SyncPreferences> {
    SyncPreferences(
      db = get<WingsLogDatabase>(),
      auth = get<FirebaseAuth>(),
      ioContext = Dispatchers.Default,
    )
  }
  single<SyncCursorStore> { SyncCursorStore(get<WingsLogDatabase>()) }
  single<SyncWriter> { FirestoreSyncWriter(get<FirebaseFirestore>()) }
  single<RemoteFetcher> { FirestoreRemoteFetcher(get<FirebaseFirestore>()) }
  single<FirestorePullSubscription> { FirestorePullSubscription(get<FirebaseFirestore>()) }
  single<HydrationRunner> {
    HydrationRunner(
      db = get<WingsLogDatabase>(),
      fetcher = get<RemoteFetcher>(),
      cursors = get<SyncCursorStore>(),
    )
  }
  single<PushWorker> {
    PushWorker(
      db = get<WingsLogDatabase>(),
      writer = get<SyncWriter>(),
      ioContext = Dispatchers.Default,
    )
  }
  single<SyncEngine> {
    val db = get<WingsLogDatabase>()
    SyncEngine(
      auth = get<FirebaseAuth>(),
      cursors = get<SyncCursorStore>(),
      pullSubscription = get<FirestorePullSubscription>(),
      hydrationRunner = get<HydrationRunner>(),
      pullListenerFactory = { kind: CollectionKind, scope: EntityScope ->
        PullListener(
          kind = kind,
          scope = scope,
          db = db
        )
      },
      pushWorker = get<PushWorker>(),
      storeFactory = get<EntityStoreFactory>(),
      syncPreferences = get<SyncPreferences>(),
      ioContext = Dispatchers.Default,
    )
  }
}
