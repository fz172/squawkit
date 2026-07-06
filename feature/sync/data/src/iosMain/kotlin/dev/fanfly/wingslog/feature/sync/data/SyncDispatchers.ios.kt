package dev.fanfly.wingslog.feature.sync.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val syncIoContext: CoroutineContext = Dispatchers.IO
