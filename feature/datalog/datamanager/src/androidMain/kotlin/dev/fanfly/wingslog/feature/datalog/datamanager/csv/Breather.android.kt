package dev.fanfly.wingslog.feature.datalog.datamanager.csv

import kotlinx.coroutines.yield

/** A real thread, so handing it back to the scheduler is all this has to do. */
internal actual suspend fun releaseThread() = yield()
