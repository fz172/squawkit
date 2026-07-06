package dev.fanfly.wingslog.feature.sync.data

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val syncIoContext: CoroutineContext = Dispatchers.Default
