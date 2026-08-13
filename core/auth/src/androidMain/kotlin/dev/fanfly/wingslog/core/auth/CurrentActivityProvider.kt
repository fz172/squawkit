package dev.fanfly.wingslog.core.auth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The activity currently in the foreground, for the one auth flow on Android that needs one.
 *
 * Sign in with Apple has no native Android SDK: Firebase runs it as a generic OAuth flow in a
 * Custom Tab, and `startActivityForSignInWithProvider` takes an `Activity`, not a `Context`. Every
 * other provider here is content with the application context (Credential Manager) or hands the
 * presentation to the platform entirely (iOS's native sheet).
 *
 * Tracked through the application's own lifecycle callbacks rather than pushed in from
 * `MainActivity`, so no host has to remember to register anything — the same reasoning as the iOS
 * bridges being installed once at startup instead of per screen.
 *
 * Held **weakly**, and only cleared on destroy rather than on pause: the Custom Tab pauses the
 * activity that launched it, so clearing there would throw away the very reference the in-flight
 * flow is anchored to.
 */
class CurrentActivityProvider(application: Application) {

  private var current: WeakReference<Activity>? = null

  init {
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        // All three of created/started/resumed record, rather than resumed alone: they are
        // idempotent, and only tracking the last of them means a single missed event leaves this
        // reporting "no foreground activity" until the user leaves the app and comes back.
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
          current = WeakReference(activity)
        }

        override fun onActivityStarted(activity: Activity) {
          current = WeakReference(activity)
        }

        override fun onActivityResumed(activity: Activity) {
          current = WeakReference(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
          if (current?.get() === activity) current = null
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      }
    )
  }

  /**
   * The foreground activity, or null when there is none usable. Callers report a failure rather
   * than guessing — launching a sign-in against a finishing activity would drop the result.
   */
  fun current(): Activity? =
    current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}
