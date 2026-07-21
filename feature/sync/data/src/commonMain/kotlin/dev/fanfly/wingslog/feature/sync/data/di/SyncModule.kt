package dev.fanfly.wingslog.feature.sync.data.di

import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.CurrentUidProvider
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.PostWriteHook
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.sync.data.HydrationRunner
import dev.fanfly.wingslog.feature.sync.data.PullListener
import dev.fanfly.wingslog.feature.sync.data.PullSubscription
import dev.fanfly.wingslog.feature.sync.data.PushWorker
import dev.fanfly.wingslog.feature.sync.data.RemoteFetcher
import dev.fanfly.wingslog.feature.sync.data.SharedScopeJanitor
import dev.fanfly.wingslog.feature.sync.data.SyncCursorStore
import dev.fanfly.wingslog.feature.sync.data.SubscriptionSyncListener
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.sync.data.SyncPreferences
import dev.fanfly.wingslog.feature.sync.data.SyncWriter
import dev.fanfly.wingslog.feature.sync.data.impl.FirestorePullSubscription
import dev.fanfly.wingslog.feature.sync.data.impl.FirestoreRemoteFetcher
import dev.fanfly.wingslog.feature.sync.data.impl.FirestoreSyncWriter
import dev.fanfly.wingslog.feature.sync.data.syncIoContext
import dev.fanfly.wingslog.feature.sync.logging.SyncTelemetry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storage
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wires the [SyncEngine] and its collaborators. Provides the [FirebaseFirestore] singleton itself,
 * depends on `storageModule` for the database + [EntityStoreFactory], and on `commonAuthModule`
 * for [FirebaseAuth].
 */
val syncModule: Module = module {

  single<FirebaseStorage> { Firebase.storage }

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
      ioContext = syncIoContext,
      writeLock = get<DatabaseWriteLock>(),
    )
  }
  // Narrow core:storage view of the master toggle — lets datamanagers read the flag without
  // depending on feature:sync:data.
  single<CloudSyncSetting> { get<SyncPreferences>() }
  // core:storage knows nothing about Firebase, so authorship is supplied from here — the store
  // stamps it as writer_uid on every local write (design §7.5).
  single<CurrentUidProvider> {
    val auth = get<FirebaseAuth>()
    CurrentUidProvider { auth.currentUser?.uid }
  }
  single<SyncCursorStore> {
    SyncCursorStore(
      get<WingsLogDatabase>(),
      writeLock = get<DatabaseWriteLock>()
    )
  }
  single<SyncWriter> { FirestoreSyncWriter(get<FirebaseFirestore>()) }
  single<RemoteFetcher> { FirestoreRemoteFetcher(get<FirebaseFirestore>()) }
  single<PullSubscription> { FirestorePullSubscription(get<FirebaseFirestore>()) }
  single<HydrationRunner> {
    HydrationRunner(
      db = get<WingsLogDatabase>(),
      fetcher = get<RemoteFetcher>(),
      cursors = get<SyncCursorStore>(),
      writeLock = get<DatabaseWriteLock>(),
      postWriteHook = getOrNull(),
    )
  }
  single<PushWorker> {
    PushWorker(
      db = get<WingsLogDatabase>(),
      writer = get<SyncWriter>(),
      ioContext = syncIoContext,
      writeLock = get<DatabaseWriteLock>(),
      storeFactory = get<EntityStoreFactory>(),
      telemetry = get<SyncTelemetry>(),
    )
  }
  single<SubscriptionSyncListener> {
    SubscriptionSyncListener(
      firestore = get<FirebaseFirestore>(),
      db = get<WingsLogDatabase>(),
      writeLock = get<DatabaseWriteLock>(),
    )
  }
  single<SyncEngine> {
    val db = get<WingsLogDatabase>()
    val postWriteHook = getOrNull<PostWriteHook>()
    val uploadScheduler = getOrNull<UploadScheduler>()
    val writeLock = get<DatabaseWriteLock>()
    SyncEngine(
      auth = get<FirebaseAuth>(),
      cursors = get<SyncCursorStore>(),
      pullSubscription = get<PullSubscription>(),
      hydrationRunner = get<HydrationRunner>(),
      pullListenerFactory = { kind: CollectionKind, scope: EntityScope ->
        PullListener(
          kind = kind,
          scope = scope,
          db = db,
          writeLock = writeLock,
          postWriteHook = postWriteHook,
        )
      },
      pushWorker = get<PushWorker>(),
      storeFactory = get<EntityStoreFactory>(),
      syncPreferences = get<SyncPreferences>(),
      ioContext = syncIoContext,
      db = db,
      uploadScheduler = uploadScheduler,
      sharedScopeJanitor = SharedScopeJanitor(
        db = db,
        writeLock = writeLock,
        aircraftStore = get<EntityStoreFactory>().create(CollectionKind.Aircraft),
        blobs = getOrNull<LocalBlobStore>(),
      ),
      telemetry = get<SyncTelemetry>(),
      writeLock = writeLock,
      subscriptionSyncListener = get<SubscriptionSyncListener>(),
    )
  }
}
