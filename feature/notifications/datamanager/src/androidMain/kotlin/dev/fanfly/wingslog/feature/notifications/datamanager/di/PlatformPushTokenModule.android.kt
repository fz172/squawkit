package dev.fanfly.wingslog.feature.notifications.datamanager.di

import android.content.Context
import android.content.pm.PackageManager
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.notifications.datamanager.InstallIdStore
import dev.fanfly.wingslog.feature.notifications.datamanager.PushTokenRegistrar
import dev.fanfly.wingslog.feature.notifications.datamanager.impl.PushTokenRegistrarImpl
import dev.fanfly.wingslog.feature.notifications.model.PushTokenSink
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformPushTokenModule: Module = module {
  single { InstallIdStore(db = get<WingsLogDatabase>(), writeLock = get<DatabaseWriteLock>()) }

  // NOT createdAtStart: this touches Firebase, and eagerly constructing anything Firebase-backed
  // is the iOS startup landmine documented on `iosFirebaseLazyInit`. It is constructed on first
  // injection instead — which is `WingsLogFirebaseMessagingService`, i.e. the moment a token
  // actually exists to register.
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
