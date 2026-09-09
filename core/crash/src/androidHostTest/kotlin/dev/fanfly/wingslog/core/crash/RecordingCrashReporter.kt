package dev.fanfly.wingslog.core.crash

/** Test double that keeps what was reported, so assertions read as the report someone would see. */
class RecordingCrashReporter : CrashReporter {
  val breadcrumbs = mutableListOf<String>()
  val exceptions = mutableListOf<Throwable>()
  val userIds = mutableListOf<String?>()
  var collectionEnabled: Boolean? = null

  override fun recordException(throwable: Throwable) {
    exceptions += throwable
  }

  override fun log(message: String) {
    breadcrumbs += message
  }

  override fun setUserId(userId: String?) {
    userIds += userId
  }

  override fun setCustomKey(key: String, value: String) = Unit

  override fun setCrashCollectionEnabled(enabled: Boolean) {
    collectionEnabled = enabled
  }
}
