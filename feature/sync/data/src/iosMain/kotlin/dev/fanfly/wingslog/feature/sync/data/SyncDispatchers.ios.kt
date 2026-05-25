package dev.fanfly.wingslog.feature.sync.data

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val syncIoContext: CoroutineContext = Dispatchers.IO
