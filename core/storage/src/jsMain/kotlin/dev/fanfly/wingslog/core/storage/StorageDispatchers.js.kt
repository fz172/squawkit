package dev.fanfly.wingslog.core.storage

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

// JS is single-threaded; there is no Dispatchers.IO. Default is the right pool for web.
internal actual val storageIoContext: CoroutineContext = Dispatchers.Default
