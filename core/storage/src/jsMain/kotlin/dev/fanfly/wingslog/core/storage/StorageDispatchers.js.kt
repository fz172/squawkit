package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

// JS is single-threaded; there is no Dispatchers.IO. Default is the right pool for web.
internal actual val storageIoContext: CoroutineContext = Dispatchers.Default
