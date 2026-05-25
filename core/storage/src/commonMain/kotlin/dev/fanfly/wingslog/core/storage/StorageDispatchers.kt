package dev.fanfly.wingslog.core.storage

import kotlin.coroutines.CoroutineContext

/**
 * IO dispatcher for storage work. `Dispatchers.IO` exists on JVM/Native but not on JS, so it is
 * provided per platform: `Dispatchers.IO` on Android/iOS, `Dispatchers.Default` on web.
 */
internal expect val storageIoContext: CoroutineContext
