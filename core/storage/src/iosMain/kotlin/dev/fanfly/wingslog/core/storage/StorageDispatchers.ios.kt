package dev.fanfly.wingslog.core.storage

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val storageIoContext: CoroutineContext = Dispatchers.IO
