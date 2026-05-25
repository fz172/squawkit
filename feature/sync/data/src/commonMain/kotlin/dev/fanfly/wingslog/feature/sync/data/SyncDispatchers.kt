package dev.fanfly.wingslog.feature.sync.data

import kotlin.coroutines.CoroutineContext

/** Platform-appropriate context for sync database observation and orchestration. */
internal expect val syncIoContext: CoroutineContext
