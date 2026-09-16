package dev.fanfly.wingslog.feature.datalog.datamanager.csv

import kotlinx.coroutines.delay

/**
 * A timer, not a yield.
 *
 * `yield()` re-enqueues onto the same JS dispatcher, which drains its queue inside the task it is
 * already running in — so the coroutine resumes and the browser still never renders. `delay` goes
 * through `setTimeout`, which ends the task, and ending the task is the only thing that lets a
 * frame be painted.
 */
internal actual suspend fun releaseThread() = delay(1)
