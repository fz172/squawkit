package dev.fanfly.wingslog.feature.datalog.datamanager.csv

import kotlin.time.TimeSource

/**
 * Hands the thread back often enough that a parse never costs a frame.
 *
 * **Counting rows was the wrong budget.** Every parser used to release after a fixed number of
 * rows, which on a 46 MB file meant hundreds of releases on a phone that needed none, and on the
 * web build meant a freeze anyway: releasing every 500 rows says nothing about how long 500 rows
 * took, and the row that matters is the one that crosses 16 ms.
 *
 * So this measures instead. A caller offers the thread back after every row; it is taken only once
 * the budget has actually been spent, which is bounded by wall clock rather than by file size.
 */
internal class Breather(private val budget: Long = FRAME_MILLIS) {

  private var since = TimeSource.Monotonic.markNow()

  suspend fun breathe() {
    if (since.elapsedNow().inWholeMilliseconds < budget) return
    releaseThread()
    since = TimeSource.Monotonic.markNow()
  }

  private companion object {
    /** One frame at 60 Hz. Past this the browser has already missed one. */
    const val FRAME_MILLIS = 12L
  }
}

/**
 * Gives the event loop, or the scheduler, a turn.
 *
 * Platform-specific because `yield()` is not enough on the web build: the JS dispatcher drains its
 * own queue inside one task, so a yielded coroutine resumes without the browser ever getting to
 * render. Only a real timer gives it that chance.
 */
internal expect suspend fun releaseThread()
