package dev.fanfly.wingslog.core.appinfo

/**
 * Bridge for iOS in-app review. StoreKit's current API, `AppStore.requestReview(in:)`, is
 * Swift-only (its Objective-C predecessor `SKStoreReviewController` is deprecated), so the host
 * installs the call via `MainEntry.installAppReviewRequester`, the same shape as the ads and
 * consent bridges. Left uninstalled → [requestReview] reports false and the caller opens the
 * listing instead.
 */
object IosAppReviewBridge {
  private var requester: (() -> Boolean)? = null

  /** [requester] returns true once it has handed the request to StoreKit, false if it could not. */
  fun install(requester: () -> Boolean) {
    this.requester = requester
  }

  fun requestReview(): Boolean = requester?.invoke() ?: false
}
