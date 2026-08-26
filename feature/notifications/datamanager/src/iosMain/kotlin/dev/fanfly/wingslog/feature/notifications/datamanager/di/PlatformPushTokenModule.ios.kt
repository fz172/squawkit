package dev.fanfly.wingslog.feature.notifications.datamanager.di

import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.notifications.datamanager.InstallIdStore
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.datamanager.impl.PushTokenRegistrarImpl
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Mirrors the Android actual (issue #506) — see its comments for the shape and reasoning.
 *
 * No [dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenBootstrap]-equivalent here: that
 * class exists to proactively read the *current* token at Koin start, but there is no Kotlin-callable
 * "read the current FCM token" API on iOS — `FirebaseMessaging` is a third-party framework only Swift
 * links. `iosApp.swift`'s `AppDelegate` does the proactive read itself (`Messaging.messaging().token
 * { ... }` in `didFinishLaunchingWithOptions`) and forwards through `MainEntry.onPushTokenReceived`,
 * the same entry point its `MessagingDelegate` rotation callback uses.
 *
 * No [dev.fanfly.wingslog.feature.notifications.model.SignedInUid] binding: Android's exists solely
 * to feed `WingsLogFirebaseMessagingService`, which has no iOS counterpart — the iOS notification
 * service extension (P5.2) is deliberately pure Swift with no Koin graph of its own, reading the
 * signed-in uid straight from `FirebaseAuth` on its own side.
 */
actual val platformPushTokenModule: Module = module {
  single {
    InstallIdStore(
      db = get<WingsLogDatabase>(),
      writeLock = get<DatabaseWriteLock>()
    )
  }

  single<PushTokenRegistrar> {
    PushTokenRegistrarImpl(
      firebaseAuth = get<FirebaseAuth>(),
      firestore = get<FirebaseFirestore>(),
      installIdStore = get<InstallIdStore>(),
      platform = "ios",
    )
  }

  // Same instance under its :model face — see the Android actual's comment on why this matters
  // (a second registrar would mean two authStateChanged collectors racing to write the same doc).
  single<PushTokenSink> { get<PushTokenRegistrar>() }
}
