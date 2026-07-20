package dev.fanfly.wingslog.feature.sync.data.blob

import platform.Foundation.NSBundle
import platform.Foundation.NSDictionary
import platform.Foundation.dictionaryWithContentsOfFile

/**
 * Reads `PROJECT_ID` from `GoogleService-Info.plist` in the app bundle — the same file
 * `FirebaseApp.configure()` reads — because gitlive's `Firebase.app.options` getter NPEs on iOS.
 */
internal actual fun firebaseProjectId(): String? {
  val path = NSBundle.mainBundle.pathForResource("GoogleService-Info", ofType = "plist")
    ?: return null
  val dict = NSDictionary.dictionaryWithContentsOfFile(path) ?: return null
  return dict["PROJECT_ID"] as? String
}
