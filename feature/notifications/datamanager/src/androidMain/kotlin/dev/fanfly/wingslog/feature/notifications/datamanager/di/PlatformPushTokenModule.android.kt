package dev.fanfly.wingslog.feature.notifications.datamanager.di

import android.content.Context
import android.content.pm.PackageManager
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.notifications.datamanager.InstallIdStore
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenBootstrap
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.datamanager.impl.PushTokenRegistrarImpl
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import dev.fanfly.wingslog.feature.notifications.model.SignedInUid
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformPushTokenModule: Module = module {
  single { InstallIdStore(db = get<WingsLogDatabase>(), writeLock = get<DatabaseWriteLock>()) }

  // Lazy, and pulled up by PushTokenBootstrap below rather than by its own createdAtStart: the
  // eager-Firebase startup landmine on `iosFirebaseLazyInit` is about iOS, and this is the Android
  // actual (the iOS one is an empty module), so eager construction is safe here — but keeping the
  // registrar itself lazy means the one place that decides "now is the time to touch Firebase" is
  // the bootstrap, not two singletons that both think they are first.
  single<PushTokenRegistrar> {
    PushTokenRegistrarImpl(
      firebaseAuth = get<FirebaseAuth>(),
      firestore = get<FirebaseFirestore>(),
      installIdStore = get<InstallIdStore>(),
      platform = "android",
      appVersion = androidContext().appVersionName(),
    )
  }

  // The same instance under its `:model` face, because Koin resolves by exact type and the FCM
  // service can only ask for PushTokenSink — `:viewing` cannot see PushTokenRegistrar at all (§3).
  // `get<PushTokenRegistrar>()` rather than a second constructor call: two registrars would mean
  // two authStateChanged collectors racing to write the same doc.
  single<PushTokenSink> { get<PushTokenRegistrar>() }

  // Bound beside PushTokenSink because it exists for the same reason: the FCM service in `:viewing`
  // needs an answer only `core:auth` has, and `:viewing` cannot depend on it. Android-only for now —
  // iOS binds its own when P5 gives it a receiver to guard.
  single<SignedInUid> {
    val auth = get<FirebaseAuth>()
    SignedInUid { auth.currentUser?.uid }
  }

  // createdAtStart, and that is the entire point: `onNewToken` only fires for a token that does not
  // exist yet, so without something that reads the *current* token at startup a device that already
  // had one never registers at all. See PushTokenBootstrap's KDoc for the failure this fixes.
  single(createdAtStart = true) { PushTokenBootstrap(sink = get<PushTokenSink>()) }
}

/**
 * Read once at Koin-build time. `versionName` cannot change during a process's lifetime, and the
 * registrar is a long-lived singleton — re-reading it per write would be a package-manager call per
 * token refresh for a value that is fixed at install.
 */
private fun Context.appVersionName(): String = try {
  packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
} catch (_: PackageManager.NameNotFoundException) {
  "Unknown"
}
