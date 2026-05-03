package dev.fanfly.wingslog.core.sync.di

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.core.sync.FirestorePullSubscription
import dev.fanfly.wingslog.core.sync.FirestoreRemoteFetcher
import dev.fanfly.wingslog.core.sync.FirestoreSyncWriter
import dev.fanfly.wingslog.core.sync.HydrationRunner
import dev.fanfly.wingslog.core.sync.PullListener
import dev.fanfly.wingslog.core.sync.PushWorker
import dev.fanfly.wingslog.core.sync.RemoteFetcher
import dev.fanfly.wingslog.core.sync.SyncCursorStore
import dev.fanfly.wingslog.core.sync.SyncEngine
import dev.fanfly.wingslog.core.sync.SyncWriter
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the [SyncEngine] and its collaborators. Depends on `storageModule` for the database +
 * [EntityStoreFactory], on `commonFirebaseModule` for [FirebaseFirestore], and on `authModule` for
 * [FirebaseAuth].
 */
val syncModule: Module = module {
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
      writer = get<SyncWriter>(),
      fetcher = get<RemoteFetcher>(),
      pullSubscription = get<FirestorePullSubscription>(),
      hydrationRunner = get<HydrationRunner>(),
      pullListenerFactory = { kind: CollectionKind, scope: EntityScope ->
        PullListener(kind = kind, scope = scope, db = db)
      },
      pushWorker = get<PushWorker>(),
      storeFactory = get<EntityStoreFactory>(),
      ioContext = Dispatchers.Default,
    )
  }
}
