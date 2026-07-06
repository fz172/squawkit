package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val storageIoContext: CoroutineContext = Dispatchers.IO
