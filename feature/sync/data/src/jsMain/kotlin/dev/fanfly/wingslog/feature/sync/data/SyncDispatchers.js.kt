package dev.fanfly.wingslog.feature.sync.data

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

internal actual val syncIoContext: CoroutineContext = Dispatchers.Default
